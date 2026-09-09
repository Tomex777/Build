# Maia-3 local model

Mirror Chess expects the Maia-3 5M fp16 ONNX export used by the app's encoder.

## Normal app flow

Open **Play → Settings → AI Model** and choose **Download Maia model**. The app downloads over HTTPS into app-private storage and verifies SHA-256 before installation.

You may instead tap **Import ONNX manually** and select a local copy. It is subjected to the same checksum verification and runtime startup check.

Expected SHA-256:

`ca22fc3031975932e693f9758149302efc177749165443ed52de828add8864fa`

Source project used for the export:
`bqrio/maia3-onnx` on Hugging Face.

Direct model URL configured in `ModelManager.kt`:
`https://huggingface.co/bqrio/maia3-onnx/resolve/f2582c005a63a034e493d93736ecdd6291dd82e7/maia3-5m.fp16.onnx?download=true`

## Development asset option

For development you can still place a verified model at:

`app/src/main/assets/models/maia3-5m.fp16.onnx`

The predictor checks app-private storage first, then this asset path.

The existing scripts under `scripts/` install the development asset copy.

## Runtime contract

Inputs:
- `tokens`: float32 `[1, 64, 12]`
- `elo_self`: float32 `[1]`
- `elo_oppo`: float32 `[1]`

Outputs:
- `logits_move`: float32 `[1, 4352]`
- `logits_value`: float32 `[1, 3]`

The board encoder canonicalizes Black-to-move positions before indexing the 4,352-move vocabulary.
