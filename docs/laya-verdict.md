# AI link verdict (on-device Laya)

An optional feature: a **Laya** decision model (System-1 typed decisions,
Apache-2.0, from [Convai Innovations](https://huggingface.co/convaiinnovations/laya))
judges every inspected link for phishing / scam / malware patterns and rates
its overall suspicion — in one forward pass per question, entirely on this
phone.

## The hard boundary

- **No network. Ever.** LinkSentry has no `INTERNET` permission and cannot
  download anything. The model bundle is **imported by you** from a computer
  (Settings → AI link verdict). Every imported file is checked against the
  pinned release's SHA-256 before it is accepted; a wrong or tampered file is
  rejected, never loaded.
- **Nothing leaves the phone.** The judge runs on LiteRT locally; the URL is
  never fetched, and the verdict is computed from the URL text plus the local
  analyzer's signals only. The page itself is never loaded.
- **Informational only.** A Laya verdict never gates or blocks anything — the
  DANGER gate stays driven by the deterministic signals. The card's best case
  is worded "nothing malicious recognized", never "safe".
- **Zero permissions preserved.** The LiteRT runtime's AARs (and their Play
  "AI delivery" transitive tree) declare several permissions LinkSentry never
  uses; all are stripped at manifest-merge time with `tools:node="remove"`,
  and CI fails if any platform permission ever appears in the merged manifest.

## Where the model comes from

Pinned release: `litert-community/Laya-Multilingual-LiteRT` (multilingual
checkpoint `convaiinnovations/laya@1c5edc17`, 256-token window, wfp16
storage). Import all six files listed in Settings — hashes are pinned in
`LayaModelStore.PINNED_SHA256` and verified at import:

| file | what it is | ~size |
|---|---|---|
| `laya_ml_s256_embeds_wfp16.tflite` | main decision graph | 239 MB |
| `token_embeddings_fp16.bin` | fp16 token-embedding table | 375 MB |
| `tokenizer.json` | mmBERT BPE tokenizer | 32 MB |
| `laya_ml_act_head_fp32.tflite` | action head graph | ~1 MB |
| `laya_ml_calibration.json` | fitted temperatures | ~2 KB |
| `token_embeddings.json` | table metadata + hash | ~1 KB |

## How a verdict is produced

1. `LinkRiskQuestions` composes the state (Python-style JSON: url, host,
   scheme, path, embedded credentials, non-default port, detected signals)
   and asks four questions: `phishing` (noul), `scam` (noul), `malware`
   (noul), `suspicion` (score 0–3).
2. `LayaPromptBuilder` encodes each question into the upstream sequence
   format (`[CLS] <type> question: … [SEP] [MASK] options … [SEP] state
   [SEP]`) with the exact option-cap / squeeze / truncation rules.
3. `LayaRtEngine` runs the LiteRT graphs (GPU FP32 first, CPU fallback) on a
   dedicated thread; `LayaDecoder` gathers marker logits, applies the
   release's calibration and produces the official four-decimal answers.
4. The four answers are mapped to a typed `LinkVerdict` with an overall
   label; the Inspect screen shows the probabilities.

## Correctness

The pure-Kotlin core (`domain/laya`) is ported from the validated
`litert-community` Android host implementation. A JVM parity gate
(`LayaParityTest`) rebuilds all 201 captured question rows of the pinned
release — token ids and marker positions must match exactly, and decoding the
captured logits must reproduce the official answers bit-for-bit at four
decimals. CI downloads the pinned `tokenizer.json` (hash-checked) so the gate
always runs; without it the test skips rather than pretending to pass.

## Memory / performance

Model load (32 MB tokenizer JSON + 393 MB mmap'd embedding table + graph
compilation) happens lazily on first use and runs on one background thread;
the engine is stopped from Settings or when the feature is disabled. Expect
roughly 0.2–1 s per link on modern hardware (GPU faster, CPU slower); the
verdict card fills in a beat after the analysis by design.
