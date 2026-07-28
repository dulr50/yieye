// Loads yolov11.onnx via ONNX Runtime (MIT); decode logic ported from the YOLOv11
// reference implementation in the public-Xiangqi project (GPLv3).
// See THIRD_PARTY_NOTICES.md and LICENSE at the project root.
package com.yieye.xiangqi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * YOLOv11 (anchor-free, DFL head) 版本的棋盘/棋子检测器。
 * 与 {@link YoloV5Detector} 的区别：
 * 1. 输入采用等比例缩放 + 居中灰边填充 (letterbox)，而不是直接拉伸到 640x640；
 * 2. 输出没有单独的 objectness 通道，每个候选框直接是 [cx, cy, w, h, 15 个类别概率]；
 * 3. 输出张量是 channel-first 排布：[1, 4+类别数, 候选框数]。
 */
public class YoloV11Detector implements ChessDetector {
    private final OrtEnvironment env;
    private final OrtSession session;

    private static final int INPUT_SIZE = 640;
    private static final float CONF_THRESHOLD = 0.5f;
    private static final float NMS_THRESHOLD = 0.45f;
    private static final int NUM_CLASSES = YoloV5Detector.LABELS.length;

    public YoloV11Detector(Context context, String modelPath) throws Exception {
        env = OrtEnvironment.getEnvironment();
        byte[] modelBytes = Utils.readAsset(context, modelPath);
        session = env.createSession(modelBytes);
    }

    @Override
    public List<YoloResult> detect(Bitmap bitmap) throws Exception {
        int origW = bitmap.getWidth();
        int origH = bitmap.getHeight();

        // rate: 让长边缩放到 640，短边等比例缩放，剩余部分居中填充灰色 (letterbox)
        float rate = Math.min((float) INPUT_SIZE / origW, (float) INPUT_SIZE / origH);
        int resizedW = Math.round(origW * rate);
        int resizedH = Math.round(origH * rate);
        float xPadding = (INPUT_SIZE - resizedW) / 2f;
        float yPadding = (INPUT_SIZE - resizedH) / 2f;

        float[] imgData = letterboxToChw(bitmap, resizedW, resizedH, xPadding, yPadding);

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(imgData),
                new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});

        OrtSession.Result result = session.run(Collections.singletonMap("images", inputTensor));
        // 输出形状为 [1, 4+NUM_CLASSES, numAnchors] (channel-first)
        float[][][] output = (float[][][]) result.get(0).getValue();

        return postProcess(output[0], origW, origH, rate, xPadding, yPadding);
    }

    /**
     * 等比例缩放后居中贴到 640x640 灰色 (114/255) 画布上，再转成 CHW 归一化 float 数组。
     */
    private float[] letterboxToChw(Bitmap bitmap, int resizedW, int resizedH, float xPadding, float yPadding) {
        Bitmap canvasBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBitmap);
        canvas.drawColor(Color.rgb(114, 114, 114));

        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, resizedW, resizedH, true);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(scaled, xPadding, yPadding, paint);
        if (scaled != bitmap) scaled.recycle();

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        canvasBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        canvasBitmap.recycle();

        float[] chw = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int plane = INPUT_SIZE * INPUT_SIZE;
        for (int i = 0; i < plane; i++) {
            int p = pixels[i];
            chw[i] = ((p >> 16) & 0xFF) / 255.0f;          // R
            chw[plane + i] = ((p >> 8) & 0xFF) / 255.0f;   // G
            chw[2 * plane + i] = (p & 0xFF) / 255.0f;       // B
        }
        return chw;
    }

    private List<YoloResult> postProcess(float[][] output, int origW, int origH,
                                          float rate, float xPadding, float yPadding) {
        List<YoloResult> detections = new ArrayList<>();
        int numAnchors = output[0].length;

        for (int i = 0; i < numAnchors; i++) {
            int maxClassIdx = -1;
            float maxClassScore = -1f;
            for (int c = 0; c < NUM_CLASSES; c++) {
                float score = output[4 + c][i];
                if (score > maxClassScore) {
                    maxClassScore = score;
                    maxClassIdx = c;
                }
            }

            if (maxClassScore <= CONF_THRESHOLD) continue;

            // 输出的 cx, cy, w, h 是在 640x640 letterbox 画布坐标系下的
            float cx = output[0][i];
            float cy = output[1][i];
            float w = output[2][i];
            float h = output[3][i];

            // 去掉 letterbox 的灰边填充，并还原回原图坐标系
            float realCx = (cx - xPadding) / rate;
            float realCy = (cy - yPadding) / rate;
            float realW = w / rate;
            float realH = h / rate;

            RectF rect = new RectF(realCx - realW / 2, realCy - realH / 2,
                    realCx + realW / 2, realCy + realH / 2);
            detections.add(new YoloResult(rect, maxClassScore, maxClassIdx, YoloV5Detector.LABELS[maxClassIdx]));
        }

        return applyPerClassNMS(detections);
    }

    /**
     * 按类别分别做 NMS：同一类别内部按置信度从高到低排列，
     * 逐个保留并抑制与其 IoU 超过阈值的同类别框，避免跨类别互相误抑制。
     */
    private List<YoloResult> applyPerClassNMS(List<YoloResult> boxes) {
        List<YoloResult> results = new ArrayList<>();

        for (int labelId = 0; labelId < NUM_CLASSES; labelId++) {
            PriorityQueue<YoloResult> pq = new PriorityQueue<>(50,
                    (a, b) -> Float.compare(b.score, a.score));
            for (YoloResult box : boxes) {
                if (box.labelId == labelId) pq.add(box);
            }

            while (!pq.isEmpty()) {
                YoloResult best = pq.poll();
                results.add(best);

                List<YoloResult> remaining = new ArrayList<>(pq);
                pq.clear();
                for (YoloResult candidate : remaining) {
                    if (calculateIoU(best.rect, candidate.rect) < NMS_THRESHOLD) {
                        pq.add(candidate);
                    }
                }
            }
        }
        return results;
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
