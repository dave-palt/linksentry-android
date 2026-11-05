package com.dav3.linksentry.domain.laya

import com.dav3.linksentry.domain.model.RiskSignal
import com.dav3.linksentry.domain.model.Severity
import com.dav3.linksentry.domain.model.SignalId
import com.dav3.linksentry.domain.model.UrlFacts

/**
 * Typed verdict for one inspected link, decoded from the Laya decision
 * model's answers. Probabilities come from the calibrated decoder; [overall]
 * is a derived label over the four answers and is ALWAYS worded as an
 * appearance judgment — LinkSentry never claims a link is safe.
 */
data class LinkVerdict(
    val phishing: NoulAnswer,
    val scam: NoulAnswer,
    val malware: NoulAnswer,
    /** Expected level on the 0..3 suspicion rubric (see [LinkRiskQuestions]). */
    val suspicion: Double,
    /** Highest of the three malicious probabilities. */
    val maliciousProbability: Double,
    val overall: Overall,
) {
    enum class Overall { APPEARS_BENIGN, SUSPICIOUS, LIKELY_MALICIOUS }
}

data class NoulAnswer(
    /** Calibrated P(true) from the decoder. */
    val probability: Double,
    /** max(p, 1-p) — the decoder's noul confidence. */
    val confidence: Double,
)

/**
 * The on-device Laya question set for link risk. Question schemas use the
 * upstream preset format (one of choice/score/noul with criteria) so the
 * pinned multilingual checkpoint sees in-distribution prompts; the state is
 * a Python-style JSON object of URL facts and local signals.
 */
object LinkRiskQuestions {

    /** questionId → upstream question schema, in inference order. */
    fun all(): List<Pair<String, Map<String, Any?>>> = listOf(
        "phishing" to mapOf(
            "type" to "noul",
            "instructions" to
                "Does the URL in `url` impersonate a trusted brand or site to steal credentials, " +
                "payment details, or personal data?",
            "criteria" to mapOf(
                "true" to "phishing or credential harvesting",
                "false" to "a normal link that is not phishing",
            ),
        ),
        "scam" to mapOf(
            "type" to "noul",
            "instructions" to
                "Does the URL in `url` point to a scam, such as a fake shop, fake giveaway, " +
                "fake support page, crypto or investment fraud, or a deceptive subscription trap?",
            "criteria" to mapOf(
                "true" to "a scam or fraud page",
                "false" to "not a scam",
            ),
        ),
        "malware" to mapOf(
            "type" to "noul",
            "instructions" to
                "Does the URL in `url` distribute or lead to malware, such as a malicious " +
                "download, fake software update, exploit page, or drive-by infection?",
            "criteria" to mapOf(
                "true" to "malware distribution or an exploit page",
                "false" to "no malware",
            ),
        ),
        "suspicion" to mapOf(
            "type" to "score",
            "instructions" to "Overall, how suspicious does the URL in `url` look?",
            "criteria" to listOf(
                "genuine everyday site",
                "slightly unusual but benign",
                "suspicious: several red flags",
                "almost certainly malicious",
            ),
        ),
    )

    /**
     * The state the model judges: the raw URL, its decomposed parts, and the
     * deterministic signals LinkSentry already raised. Pure JSON-friendly
     * values only (LayaJson serializes maps Python-style).
     */
    fun buildState(facts: UrlFacts, signals: List<RiskSignal>): Map<String, Any?> {
        val parts = LinkedHashMap<String, Any?>()
        parts["url"] = facts.raw
        parts["host"] = facts.host
        if (facts.rawHost != facts.host) parts["raw_host"] = facts.rawHost
        parts["scheme"] = facts.scheme.ifEmpty { "none" }
        if (facts.path.isNotEmpty() && facts.path != "/") parts["path"] = facts.path
        if (facts.userInfo != null) parts["embedded_credentials"] = facts.userInfo
        if (facts.effectivePort != defaultPort(facts.scheme)) {
            parts["port"] = facts.effectivePort
        }
        if (signals.isNotEmpty()) {
            parts["detected_signals"] = signals.joinToString("; ") { signalLabel(it.id) }
        }
        return parts
    }

    /** Derives the summary label from the four calibrated answers. */
    fun overall(verdict: NoulAnswer, scam: NoulAnswer, malware: NoulAnswer, suspicion: Double): LinkVerdict.Overall {
        val worstNoul = maxOf(verdict.probability, scam.probability, malware.probability)
        return when {
            worstNoul >= 0.8 || suspicion >= 2.5 -> LinkVerdict.Overall.LIKELY_MALICIOUS
            worstNoul >= 0.45 || suspicion >= 1.5 -> LinkVerdict.Overall.SUSPICIOUS
            else -> LinkVerdict.Overall.APPEARS_BENIGN
        }
    }

    private fun defaultPort(scheme: String): Int = when (scheme.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }

    /** Short, model-friendly labels for the analyzer's signal ids. */
    private fun signalLabel(id: SignalId): String = when (id) {
        SignalId.CREDENTIALS_IN_URL -> "credentials embedded in the url"
        SignalId.IP_LITERAL_HOST -> "raw ip address host"
        SignalId.NONSTANDARD_PORT -> "non-standard port"
        SignalId.EXPLICIT_DEFAULT_PORT -> "explicit default port"
        SignalId.PUNYCODE_HOST -> "punycode host, possible lookalike domain"
        SignalId.MIXED_CASE_HOST -> "mixed-case host"
        SignalId.SHORTENER_HOST -> "url shortener, destination hidden"
        SignalId.TRACKING_PARAMS -> "tracking parameters"
        SignalId.DANGEROUS_SCHEME -> "dangerous url scheme"
        SignalId.VERY_LONG_URL -> "very long url"
        SignalId.DEEP_PATH -> "deep path structure"
        SignalId.DOTLESS_HOST -> "dotless host"
    }

    /** Maps a decoded official noul answer dictionary to the typed form. */
    fun noulFrom(answer: Map<String, Any?>): NoulAnswer {
        val p = (answer["noul"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("noul answer missing 'noul': $answer")
        val confidence = (answer["confidence"] as? Number)?.toDouble() ?: maxOf(p, 1.0 - p)
        return NoulAnswer(probability = p, confidence = confidence)
    }

    /** Builds the typed [LinkVerdict] from decoded answer maps by question id. */
    fun verdictFrom(answers: Map<String, Map<String, Any?>>): LinkVerdict {
        val phishing = noulFrom(answers.getValue("phishing"))
        val scam = noulFrom(answers.getValue("scam"))
        val malware = noulFrom(answers.getValue("malware"))
        val suspicion = (answers.getValue("suspicion")["score"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("suspicion answer missing 'score'")
        val overall = overall(phishing, scam, malware, suspicion)
        return LinkVerdict(
            phishing = phishing,
            scam = scam,
            malware = malware,
            suspicion = suspicion,
            maliciousProbability = maxOf(phishing.probability, scam.probability, malware.probability),
            overall = overall,
        )
    }

    /** Severity bucket for history/UI color reuse; null-never-DANGER: Laya alone never gates. */
    fun severityOf(verdict: LinkVerdict): Severity = when (verdict.overall) {
        LinkVerdict.Overall.LIKELY_MALICIOUS -> Severity.DANGER
        LinkVerdict.Overall.SUSPICIOUS -> Severity.WARN
        LinkVerdict.Overall.APPEARS_BENIGN -> Severity.INFO
    }
}
