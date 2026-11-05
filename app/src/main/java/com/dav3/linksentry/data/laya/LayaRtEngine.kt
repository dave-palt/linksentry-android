package com.dav3.linksentry.data.laya

import android.content.Context
import com.dav3.linksentry.domain.laya.LayaCalibration
import com.dav3.linksentry.domain.laya.LayaDecoder
import com.dav3.linksentry.domain.laya.LayaEmbeddings
import com.dav3.linksentry.domain.laya.LayaPromptBuilder
import com.dav3.linksentry.domain.laya.LayaRuntime
import com.dav3.linksentry.domain.laya.LayaRuntimeState
import com.dav3.linksentry.domain.laya.LayaSequence
import com.dav3.linksentry.domain.laya.LayaTokenizer
import com.dav3.linksentry.domain.laya.LinkRiskQuestions
import com.dav3.linksentry.domain.laya.LinkVerdict
import com.dav3.linksentry.domain.model.RiskSignal
import com.dav3.linksentry.domain.model.UrlFacts
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LiteRT runtime for the pinned multilingual Laya checkpoint (window 256,
 * wfp16 storage). One question per graph invocation, exactly like the
 * upstream host contract; the whole judge() runs on a dedicated thread.
 *
 * GPU (explicit FP32) is tried first; on any GPU creation failure the engine
 * falls back to a 4-thread CPU session — the model and arithmetic are
 * identical, only the accelerator changes.
 */
class LayaRtEngine(
    private val context: Context,
    private val modelDir: File,
) : Closeable {

    private enum class Backend(val accelerator: Accelerator) {
        GPU(Accelerator.GPU),
        CPU(Accelerator.CPU),
    }

    private class Graph(
        val model: CompiledModel,
        val inputs: Map<String, TensorBuffer>,
        val outputs: Map<String, TensorBuffer>,
    ) : Closeable {
        override fun close() {
            try {
                (inputs.values + outputs.values).forEach { it.close() }
            } finally {
                model.close()
            }
        }
    }

    private val tokenizer: LayaTokenizer
    private val calibration: LayaCalibration
    private val embeddings: LayaEmbeddings
    private var main: Graph? = null
    private var act: Graph? = null

    /** True once both graphs are compiled. */
    val isReady: Boolean
        get() = main != null && act != null

    init {
        tokenizer = LayaTokenizer(requireFile("tokenizer.json"))
        calibration = LayaCalibration.load(requireFile("laya_ml_calibration.json"))
        embeddings = LayaEmbeddings(
            tableFile = requireFile("token_embeddings_fp16.bin"),
            metadataFile = requireFile("token_embeddings.json"),
        )
    }

    /**
     * Compiles both graphs on the GPU and retries on CPU when GPU creation
     * fails (no GPU delegate on this device, driver rejection, ...).
     * Called on the engine thread before the first judge.
     */
    fun initialize() = onEngineThread {
        closeGraphs()
        try {
            main = createMain(Backend.GPU)
            act = createAct(Backend.GPU)
            Timber.i("Laya engine ready on GPU")
        } catch (t: Throwable) {
            Timber.w(t, "GPU init failed; falling back to CPU")
            closeGraphs()
            main = createMain(Backend.CPU)
            act = createAct(Backend.CPU)
            Timber.i("Laya engine ready on CPU")
        }
    }

    /** Judges one link; null when graphs are not compiled or a row fails. */
    suspend fun judge(facts: UrlFacts, signals: List<RiskSignal>): LinkVerdict? = withContext(Dispatchers.Default) {
        onEngineThread<LinkVerdict?> {
            val graph = main ?: return@onEngineThread null
            val actGraph = act ?: return@onEngineThread null
            val builder = LayaPromptBuilder(tokenizer, maxLen = WINDOW, headMaxLen = HEAD_MAX_LEN)
            val state = LinkRiskQuestions.buildState(facts, signals)
            val answers = LinkedHashMap<String, Map<String, Any?>>()
            for ((id, schema) in LinkRiskQuestions.all()) {
                val sequence = try {
                    builder.build(state, schema, questionId = id)
                } catch (t: IllegalArgumentException) {
                    // Upstream rejection rule: options exceed the head budget.
                    Timber.i("Laya question %s rejected by builder: %s", id, t.message)
                    return@onEngineThread null
                }
                val raw = runRow(graph, actGraph, sequence) ?: return@onEngineThread null
                answers[id] = LayaDecoder.decode(raw.first, raw.second, sequence.question, calibration)
            }
            try {
                LinkRiskQuestions.verdictFrom(answers)
            } catch (t: IllegalArgumentException) {
                Timber.w(t, "Laya answer mapping failed")
                null
            }
        }
    }

    /** One main invocation + action head, returning (markerLogits, actLogits). */
    private fun runRow(
        graph: Graph,
        actGraph: Graph,
        sequence: LayaSequence,
    ): Pair<FloatArray, FloatArray>? {
        val attention = FloatArray(WINDOW) { if (it < sequence.ids.size) 1f else 0f }
        val qtype = FloatArray(3).also { it[sequence.question.qtype] = 1f }
        val embeds = FloatArray(WINDOW * LayaEmbeddings.WIDTH)
        embeddings.gather(sequence.ids, WINDOW, embeds)
        graph.inputs.getValue("inputs_embeds").writeFloat(embeds)
        graph.inputs.getValue("attention_mask").writeFloat(attention)
        graph.inputs.getValue("qtype_onehot").writeFloat(qtype)
        graph.model.run(graph.inputs, graph.outputs, SIGNATURE)
        val tokens = graph.outputs.getValue("token_logits").readFloat()
        val pooled = graph.outputs.getValue("pooled_cls").readFloat()
        if (tokens.size != WINDOW || pooled.size != 768) {
            Timber.w("Unexpected Laya main graph output shape: %d/%d", tokens.size, pooled.size)
            return null
        }
        if (!tokens.all { it.isFinite() } || !pooled.all { it.isFinite() }) {
            Timber.w("Nonfinite Laya main graph output; dropping verdict")
            return null
        }
        val markerLogits = LayaDecoder.gather(tokens, sequence.markers)
        val features = LayaDecoder.actFeatures(markerLogits)
        actGraph.inputs.getValue("pooled_cls").writeFloat(pooled)
        actGraph.inputs.getValue("feats").writeFloat(features)
        actGraph.model.run(actGraph.inputs, actGraph.outputs, SIGNATURE)
        val actLogits = actGraph.outputs.getValue("act_logits").readFloat()
        if (actLogits.size != 2 || !actLogits.all { it.isFinite() }) {
            Timber.w("Nonfinite or malformed Laya action head output")
            return null
        }
        return markerLogits to actLogits
    }

    private fun createMain(backend: Backend): Graph = createGraph(
        filename = "laya_ml_s256_embeds_wfp16.tflite",
        backend = backend,
        inputNames = listOf("attention_mask", "inputs_embeds", "qtype_onehot"),
        outputNames = listOf("pooled_cls", "token_logits"),
    )

    private fun createAct(backend: Backend): Graph = createGraph(
        filename = "laya_ml_act_head_fp32.tflite",
        backend = backend,
        inputNames = listOf("feats", "pooled_cls"),
        outputNames = listOf("act_logits"),
    )

    private fun createGraph(
        filename: String,
        backend: Backend,
        inputNames: List<String>,
        outputNames: List<String>,
    ): Graph {
        val options = CompiledModel.Options(backend.accelerator).apply {
            when (backend) {
                Backend.GPU ->
                    gpuOptions = CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP32)
                Backend.CPU -> cpuOptions = CompiledModel.CpuOptions(numThreads = 4)
            }
        }
        val model = CompiledModel.create(
            requireFile(filename).absolutePath,
            options,
            LayaEngineThread.environment(context),
        )
        val inputs = linkedMapOf<String, TensorBuffer>()
        val outputs = linkedMapOf<String, TensorBuffer>()
        try {
            inputNames.forEach { inputs[it] = model.createInputBuffer(it, SIGNATURE) }
            outputNames.forEach { outputs[it] = model.createOutputBuffer(it, SIGNATURE) }
            return Graph(model, inputs, outputs)
        } catch (failure: Throwable) {
            (inputs.values + outputs.values).forEach { it.close() }
            model.close()
            throw failure
        }
    }

    private fun requireFile(name: String): File = File(modelDir, name).also { check(it.isFile) { "Missing Laya model file: $name" } }

    private fun closeGraphs() {
        main?.let { runClosing(it) }
        act?.let { runClosing(it) }
        main = null
        act = null
    }

    private inline fun runClosing(graph: Graph) {
        try {
            graph.close()
        } catch (t: Throwable) {
            Timber.w(t, "Laya graph close failed")
        }
    }

    override fun close() {
        onEngineThread {
            closeGraphs()
            embeddings.close()
        }
    }

    /** Runs [block] on the dedicated engine thread and unwraps its failure. */
    private fun <T> onEngineThread(block: () -> T): T = try {
        LayaEngineThread.executor.submit(Callable { block() }).get()
    } catch (failure: ExecutionException) {
        throw failure.cause ?: failure
    }

    companion object {
        private const val SIGNATURE = "serving_default"
        private const val WINDOW = 256
        private const val HEAD_MAX_LEN = 256

        /** The runtime pin this engine was validated against. */
        const val LITERT_VERSION = "2.3.0"
    }
}

/** One native thread + one LiteRT Environment for the whole process. */
private object LayaEngineThread {
    val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LinkSentry-Laya").apply { isDaemon = true }
        }

    private var sharedEnvironment: Environment? = null

    // Both directories point at the packaged vendor libraries; without
    // CompilerPluginLibraryDir an NPU-bound model would not compile (harmless
    // for GPU and CPU, same setup the upstream sample validates).
    fun environment(context: Context): Environment = sharedEnvironment ?: Environment.create(
        context,
        mapOf(
            Environment.Option.DispatchLibraryDir to context.applicationInfo.nativeLibraryDir,
            Environment.Option.CompilerPluginLibraryDir to context.applicationInfo.nativeLibraryDir,
        ),
    ).also { sharedEnvironment = it }
}

/**
 * Process-wide controller over one optional [LayaRtEngine]. Created lazily
 * the first time a verdict is requested with a verified model installed —
 * never at app start, so the app stays tiny for users who skip the feature.
 */
@Singleton
class LayaController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelStore: LayaModelStore,
) : LayaRuntime {
    private val _state = MutableStateFlow<LayaRuntimeState>(LayaRuntimeState.Unavailable)
    override val state: StateFlow<LayaRuntimeState> = _state.asStateFlow()

    private var engine: LayaRtEngine? = null

    val isReady: Boolean
        get() = _state.value is LayaRuntimeState.Ready

    /**
     * Ensures the engine is loading/ready when a verified bundle exists.
     * Safe to call repeatedly; skipped entirely when nothing is installed
     * or the user disabled the feature.
     */
    override fun ensureStarted(enabled: Boolean) {
        if (!enabled) return
        if (_state.value is LayaRuntimeState.Loading || _state.value is LayaRuntimeState.Ready) return
        when (modelStore.status()) {
            is LayaModelStatus.Installed -> startLoading()
            else -> _state.value = LayaRuntimeState.Unavailable
        }
    }

    private fun startLoading() {
        _state.value = LayaRuntimeState.Loading
        LayaEngineThread.executor.submit {
            engine?.close()
            engine = null
            loadLocked()
        }
    }

    /** Reloads the engine after a new import; disables when [enabled] is false. */
    override fun restart(enabled: Boolean) {
        LayaEngineThread.executor.submit {
            engine?.close()
            engine = null
            if (enabled && modelStore.status() is LayaModelStatus.Installed) {
                _state.value = LayaRuntimeState.Loading
                loadLocked()
            } else {
                _state.value = LayaRuntimeState.Unavailable
            }
        }
    }

    private fun loadLocked() {
        try {
            val fresh = LayaRtEngine(context, modelStore.modelDir)
            fresh.initialize()
            engine = fresh
            _state.value = LayaRuntimeState.Ready
        } catch (t: Throwable) {
            Timber.w(t, "Laya engine init failed")
            engine = null
            _state.value = LayaRuntimeState.Failed(reason = t.message ?: t.javaClass.simpleName)
        }
    }

    /** Stops the engine and forgets the model dir reference (delete). */
    override fun stop() {
        LayaEngineThread.executor.submit {
            engine?.close()
            engine = null
            _state.value = LayaRuntimeState.Unavailable
        }
    }

    override suspend fun judge(facts: UrlFacts, signals: List<RiskSignal>): LinkVerdict? {
        val active = engine ?: return null
        return try {
            active.judge(facts, signals)
        } catch (t: Throwable) {
            Timber.w(t, "Laya judge failed")
            null
        }
    }

    fun shutdown() = stop()
}
