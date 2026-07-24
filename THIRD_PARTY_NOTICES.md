# Third-Party Notices

This project is licensed under the **GNU General Public License v3.0** (see [LICENSE](LICENSE)),
as required by the third-party GPLv3 components it incorporates.

## Pikafish

- Source: https://github.com/official-pikafish/Pikafish
- License: GNU GPLv3
- Used for: Xiangqi (Chinese Chess) move search/analysis engine.
- Location in this repo: `app/src/main/cpp/Pikafish-Pikafish-2026-01-02/`,
  `app/src/main/cpp/pikafish_jni.cpp`, `app/src/main/assets/pikafish.nnue`.
- Pikafish is itself derived from Stockfish (GNU GPLv3).

## VinXiangQi

- Source: https://github.com/Vincentzyx/VinXiangQi  (VinXiangQi Windows project — see `VinXiangQi-1.4.0/`)
- License: GNU GPLv3 (see `VinXiangQi-1.4.0/LICENSE`)
- Used for: `app/src/main/assets/middle.onnx`, a YOLOv5-based board/piece
  detection model used for recognizing the chessboard from screenshots/photos.

## ONNX Runtime

- Source: https://github.com/microsoft/onnxruntime
- License: MIT License
- Used for: running the `.onnx` board-detection model on Android
  (`ai.onnxruntime.*` in `app/src/main/java/com/demo/mychess/YoloV5Detector.java`).

---

If you redistribute this project, you must comply with the GNU GPLv3 for the
project as a whole, and retain the copyright and license notices of the
above components.
