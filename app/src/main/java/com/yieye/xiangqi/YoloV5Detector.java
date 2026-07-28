// Loads middle.onnx via ONNX Runtime (MIT); the model itself is derived from the
// VinXiangQi project (GPLv3). See THIRD_PARTY_NOTICES.md and LICENSE at the project root.
package com.yieye.xiangqi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.Log;
//
//import com.microsoft.onnxruntime.OnnxTensor;
//import com.microsoft.onnxruntime.OrtEnvironment;
//import com.microsoft.onnxruntime.OrtSession;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class YoloV5Detector implements ChessDetector {
    private static final String TAG = "YoloV5Detector";
    private final OrtEnvironment env;
    private final OrtSession session;

    private static final int INPUT_WIDTH = 640;
    private static final int INPUT_HEIGHT = 640;
    private static final float CONF_THRESHOLD = 0.5f;
    private static final float NMS_THRESHOLD = 0.45f;

    public static final String[] LABELS = {
            "b_ma", "b_xiang", "b_shi", "b_jiang", "b_che", "b_pao", "b_bing",
            "r_che", "r_ma", "r_shi", "r_jiang", "r_xiang", "r_pao", "r_bing", "board"
    };

    public YoloV5Detector(Context context, String modelPath) throws Exception {
        env = OrtEnvironment.getEnvironment();
        byte[] modelBytes = Utils.readAsset(context, modelPath);
        session = env.createSession(modelBytes);
    }

    @Override
    public List<YoloResult> detect(Bitmap bitmap) throws Exception {
        // 使用保持比例的缩放 (Letterbox 思想的简化版)
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true);

        float[] imgData = new float[1 * 3 * INPUT_WIDTH * INPUT_HEIGHT];
        for (int y = 0; y < INPUT_HEIGHT; y++) {
            for (int x = 0; x < INPUT_WIDTH; x++) {
                int pixel = resizedBitmap.getPixel(x, y);
                // 绝大多数导出到 ONNX 的 YOLOv5 模型使用 0-1 归一化
                imgData[y * INPUT_WIDTH + x] = Color.red(pixel) / 255.0f;
                imgData[INPUT_WIDTH * INPUT_HEIGHT + y * INPUT_WIDTH + x] = Color.green(pixel) / 255.0f;
                imgData[2 * INPUT_WIDTH * INPUT_HEIGHT + y * INPUT_WIDTH + x] = Color.blue(pixel) / 255.0f;
            }
        }

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(imgData), new long[]{1, 3, INPUT_WIDTH, INPUT_HEIGHT});
        
        OrtSession.Result result = session.run(Collections.singletonMap("images", inputTensor));
        float[][][] output = (float[][][]) result.get(0).getValue();

        return postProcess(output[0], bitmap.getWidth(), bitmap.getHeight());
    }

    private List<YoloResult> postProcess(float[][] output, int origW, int origH) {
        List<YoloResult> detections = new ArrayList<>();

        for (float[] row : output) {
            float objConf = row[4];
            if (objConf > CONF_THRESHOLD) {
                int maxClassIdx = -1;
                float maxClassProb = -1f;
                for (int i = 5; i < 20; i++) {
                    if (row[i] > maxClassProb) {
                        maxClassProb = row[i];
                        maxClassIdx = i - 5;
                    }
                }

                float finalScore = objConf * maxClassProb;
                if (finalScore > CONF_THRESHOLD) {
//                    LogUtil.d(TAG, "Detected Index: " + maxClassIdx + " Score: " + finalScore);
                    float cx = row[0] * origW / (float) INPUT_WIDTH;
                    float cy = row[1] * origH / (float) INPUT_HEIGHT;
                    float w = row[2] * origW / (float) INPUT_WIDTH;
                    float h = row[3] * origH / (float) INPUT_HEIGHT;
                    detections.add(new YoloResult(
                            new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2),
                            finalScore, maxClassIdx, LABELS[maxClassIdx]
                    ));
                }
            }
        }
        return applyNMS(detections);
    }

    private List<YoloResult> applyNMS(List<YoloResult> boxes) {
        if (boxes.isEmpty()) return boxes;

        Collections.sort(boxes, (a, b) -> Float.compare(b.score, a.score));
        List<YoloResult> selected = new ArrayList<>();
        boolean[] removed = new boolean[boxes.size()];

        for (int i = 0; i < boxes.size(); i++) {
            if (removed[i]) continue;
            YoloResult best = boxes.get(i);
            selected.add(best);
            for (int j = i + 1; j < boxes.size(); j++) {
                if (!removed[j] && calculateIoU(best.rect, boxes.get(j).rect) > NMS_THRESHOLD) {
                    removed[j] = true;
                }
            }
        }
        return selected;
    }

    private float calculateIoU(RectF a, RectF b) {
        float interLeft = Math.max(a.left, b.left);
        float interTop = Math.max(a.top, b.top);
        float interRight = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);

        if (interLeft >= interRight || interTop >= interBottom) return 0f;

        float interArea = (interRight - interLeft) * (interBottom - interTop);
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);

        return interArea / (areaA + areaB - interArea);
    }
}
