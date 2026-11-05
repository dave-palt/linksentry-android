package com.dav3.linksentry.domain.laya

import com.dav3.linksentry.domain.model.RiskSignal
import com.dav3.linksentry.domain.model.UrlFacts
import kotlinx.coroutines.flow.StateFlow

/** Lifetime state of the optional on-device Laya verdict engine. */
sealed interface LayaRuntimeState {

    /** No verified model imported — the feature is dormant. */
    data object Unavailable : LayaRuntimeState

    /** Model present; tokenizer and graphs are being loaded. */
    data object Loading : LayaRuntimeState

    /** Ready to judge links. */
    data object Ready : LayaRuntimeState

    /** Loading failed; [reason] is shown in Settings. */
    data class Failed(val reason: String) : LayaRuntimeState
}

/**
 * Process-wide control surface for the optional Laya verdict engine.
 * Implemented in the data layer (LiteRT runtime); UI/ViewModels depend only
 * on this interface so tests can substitute fakes.
 *
 * The engine is fully on-device and opt-in: [ensureStarted] loads it only
 * when the user imported a verified model bundle AND enabled the feature.
 * It never touches the network — LinkSentry has no INTERNET permission.
 */
interface LayaRuntime {

    /** Current engine state (hot flow). */
    val state: StateFlow<LayaRuntimeState>

    /**
     * Starts loading when [enabled] and a verified model bundle is installed.
     * Repeated calls are no-ops while loading/ready.
     */
    fun ensureStarted(enabled: Boolean)

    /** Reloads after a (re)import; resets to [LayaRuntimeState.Unavailable] when disabled. */
    fun restart(enabled: Boolean)

    /** Releases the engine (model deleted or feature disabled). */
    fun stop()

    /**
     * Judges one link. Returns null when not ready or the run failed —
     * a null verdict never blocks or gates anything; the verdict is
     * informational only.
     */
    suspend fun judge(facts: UrlFacts, signals: List<RiskSignal>): LinkVerdict?
}
