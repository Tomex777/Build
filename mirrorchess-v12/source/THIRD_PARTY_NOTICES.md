# Third-party notices

## ONNX Runtime

Mirror Chess depends on `com.microsoft.onnxruntime:onnxruntime-android` for local ONNX inference. ONNX Runtime is distributed under the MIT License. See the upstream Microsoft ONNX Runtime project for the complete license text and notices.

## Maia-3 / maia3-onnx model

The optional neural model is not bundled in this source ZIP. The app points to the `bqrio/maia3-onnx` Maia-3 5M fp16 export and can install it at runtime after checksum verification.

The model/project has separate licensing terms from this Android shell. At the time this project was prepared, the referenced model was presented under AGPL-3.0 terms. **Review the current upstream model license before publishing or redistributing the model or an app that installs/bundles it.**

## UI/source provenance

The Android shell and chess UI in this project are clean-room code. Earlier open-source chess projects were used as visual/interaction references only; GPL source from `jlmcdonnell/chess` is not copied into this project.
