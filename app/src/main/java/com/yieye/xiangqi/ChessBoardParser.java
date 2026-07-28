// Uses middle.onnx, a board-detection model derived from the VinXiangQi project (GPLv3).
// See THIRD_PARTY_NOTICES.md and LICENSE at the project root.
package com.yieye.xiangqi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ChessBoardParser {
    private static final String TAG = "ChessBoardParser";

    public interface FenCallback {
        void onResult(String fen, List<YoloResult> results);
    }

    // true = 使用 YOLOv11 (yolov11.onnx)；false = 使用 YOLOv5 (middle.onnx)
    private static final boolean USE_YOLO_V11 = true;

    private static ChessDetector cachedDetector;
    private static RectF cachedCropRect = null;

    private static synchronized ChessDetector getDetector(Context context) throws Exception {
        if (cachedDetector == null) {
            if (USE_YOLO_V11) {
                try {
                    cachedDetector = new YoloV11Detector(context, "yolov11.onnx");
                } catch (Exception e) {
                    LogUtil.e(TAG, "YOLOv11 模型加载失败，回退使用 YOLOv5 模型: " + e.getMessage(), e);
                    cachedDetector = new YoloV5Detector(context, "middle.onnx");
                }
            } else {
                cachedDetector = new YoloV5Detector(context, "middle.onnx");
            }
        }
        return cachedDetector;
    }

    /**
     * 智能裁剪棋盘：
     * 1. 先用原图进行一次检测，找到 labelName 为 "board" 的区域。
     * 2. 以该区域中心为基准，按比例计算出一个高度与宽度匹配的“切片”。
     * 3. 这里的“放大”体现在：切片的高度是根据棋盘在原图中的占比动态调整的，
     *    从而在后续缩放到 640x640 进入 YOLO 识别时，棋盘能占据更大的比例，且不失真。
     */
    public static Bitmap smartCrop(Context context, Bitmap bitmap) {
        if (bitmap == null) return null;
        int imgWidth = bitmap.getWidth();
        int imgHeight = bitmap.getHeight();

        // 如果已经缓存了坐标，直接使用缓存裁剪，跳过 YOLO
        if (cachedCropRect != null) {
            try {
                int cropY = (int) cachedCropRect.top;
                int cropHeight = (int) cachedCropRect.height();
                
                // 确保不越界
                if (cropY >= 0 && cropY + cropHeight <= imgHeight) {
//                    LogUtil.d(TAG, "使用缓存坐标裁剪棋盘: y=" + cropY + ", height=" + cropHeight);
                    return Bitmap.createBitmap(bitmap, 0, cropY, imgWidth, cropHeight);
                } else {
//                    LogUtil.w(TAG, "缓存坐标已失效，重新识别");
                    cachedCropRect = null;
                }
            } catch (Exception e) {
//                LogUtil.e(TAG, "使用缓存裁剪失败", e);
                cachedCropRect = null;
            }
        }

        try {
            ChessDetector detector = getDetector(context);
            List<YoloResult> results = detector.detect(bitmap);
            YoloResult board = null;
            if (results != null) {
                for (YoloResult r : results) {
                    if ("board".equals(r.labelName)) {
                        board = r;
                        break;
                    }
                }
            }

            if (board != null) {
                RectF rect = board.rect;
                float boardWidth = rect.width();
                float boardHeight = rect.height();

                // 校验：宽度需占屏幕 90% 以上，且宽高比需在合理范围 (象棋棋盘 10行9列，比例约 1.11)
                float ratio = boardHeight / boardWidth;
                boolean isValidBoard = boardWidth > (imgWidth * 0.60f) && ratio > 0.8f && ratio < 1.3f;

                if (isValidBoard) {
                    // 宽度放大到原图的宽度，计算对应的放大比例
                    float scale = (float) imgWidth / boardWidth;
                    // 高度按宽度的放大比例放大
                    float targetHeight = imgWidth * 1.1f;
                    float centerY = rect.centerY();

                    // 以识别的board所在的rect中心为基础，向两边放大
                    int cropY = (int) (centerY - targetHeight / 2) + 10;
                    int cropHeight = (int) Math.round(targetHeight);

                    // 边界修正
                    if (cropY < 0) cropY = 0;
                    if (cropY + cropHeight > imgHeight) {
                        cropHeight = imgHeight - cropY;
                    }

                    if (cropHeight > 0) {
                        // 缓存这次成功的坐标
                        cachedCropRect = new RectF(0, cropY, imgWidth, cropY + cropHeight);
//                        LogUtil.d(TAG, String.format("智能裁剪棋盘成功并缓存: y=%d, h=%d, w_ratio=%.2f, r=%.2f",
//                                cropY, cropHeight, boardWidth/imgWidth, ratio));
                        return Bitmap.createBitmap(bitmap, 0, cropY, imgWidth, cropHeight);
                    }
                } else {
//                    LogUtil.w(TAG, String.format("检测到疑似棋盘但校验未通过: 宽度占比=%.2f, 宽高比=%.2f",
//                            boardWidth / imgWidth, ratio));
                }
            }
        } catch (Exception e) {
//            LogUtil.e(TAG, "Smart crop error", e);
        }
        
        // 如果未检测到棋盘或出错，回退到原来的裁剪逻辑
//        LogUtil.w(TAG, "未检测到棋盘标签，使用回退裁剪方案");
//        return ChessRecognizer.corpBitmap(context, bitmap);
        return null;
    }
    
    /**
     * 清除缓存的棋盘位置信息
     */
    public static void clearCropCache() {
        cachedCropRect = null;
    }

    public static void parse(Context context, Bitmap bitmap, FenCallback callback) {
            try {
                if (bitmap == null) {
                    callback.onResult(null, null);
                    return;
                }
                ChessDetector detector = getDetector(context);
                List<YoloResult> results = detector.detect(bitmap);
                if (results == null || results.isEmpty()) {
                    callback.onResult(null, null);
                    return;
                }
                String fen = resultsToFen(results);
                callback.onResult(fen, results);
            } catch (Exception e) {
//                LogUtil.e(TAG, "Parsing error", e);
                callback.onResult(null, null);
            } finally {
                // 识别线程负责回收它拿到的这个位图
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
    }

    private static String resultsToFen(List<YoloResult> results) {
        RectF boardRect = new RectF(-1, -1, -1, -1);
        
        // 1. 提取 Board 标签
        for (YoloResult res : results) {
            if (res.labelName.equals("board")) {
                boardRect = new RectF(res.rect);
                break;
            }
        }

        // 2. 统计棋子分布以修正棋盘边界 (移植 C# GetBoardFromPrediction 逻辑)
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -1, maxY = -1;
        List<YoloResult> validPieces = new ArrayList<>();
        
        for (YoloResult res : results) {
            if (res.labelName.equals("board") || res.labelName.equals("obstacle")) continue;
            
            // 过滤宽高比异常的
            float ratio = res.rect.width() / res.rect.height();
            if (ratio > 1.3f || ratio < 0.7f) continue;

            float cx = res.rect.centerX();
            float cy = res.rect.centerY();

            validPieces.add(res);
            if (cx < minX) minX = cx;
            if (cy < minY) minY = cy;
            if (cx > maxX) maxX = cx;
            if (cy > maxY) maxY = cy;
        }

        if (validPieces.isEmpty()) {
            return "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
        }

        // 如果识别到了board标签，则使用标签；否则使用棋子包络矩形
        if (boardRect.left == -1) {
            boardRect = new RectF(minX, minY, maxX, maxY);
        }

        // 几何自检 (移植 C# GetBoardFromPrediction 的 minDistense 校验)：
        // 棋子间距应当是均匀的，如果棋子包络矩形的跨度符合"横向8个格、纵向9个格"应有的比例，
        // 说明棋子定位比 board 标签框更可靠，用包络矩形反推网格，让棋盘对齐更精确。
        double minDistense = Double.MAX_VALUE;
        for (int i = 0; i < validPieces.size(); i++) {
            RectF a = validPieces.get(i).rect;
            for (int j = i + 1; j < validPieces.size(); j++) {
                RectF b = validPieces.get(j).rect;
                double d = Math.hypot(a.centerX() - b.centerX(), a.centerY() - b.centerY());
                if (d < minDistense) minDistense = d;
            }
        }
        double envelopeWidth = maxX - minX;
        double envelopeHeight = maxY - minY;
        if (validPieces.size() >= 2 && envelopeHeight >= minDistense * 8.6 && envelopeWidth >= minDistense * 7.6) {
            boardRect = new RectF(minX, minY, maxX, maxY);
        }

        // 3. 计算网格 (9x10 对应 8x9 个间距)
        float gridWidth = boardRect.width() / 8.0f;
        float gridHeight = boardRect.height() / 9.0f;

        String[][] grid = new String[9][10];
        boolean redSide = true; // 默认红方在下

        for (YoloResult res : validPieces) {
            float cx = res.rect.centerX();
            float cy = res.rect.centerY();

            float offsetX = cx - boardRect.left;
            float offsetY = cy - boardRect.top;

            int xPos = Math.round(offsetX / gridWidth);
            int yPos = Math.round(offsetY / gridHeight);

            if (xPos >= 0 && xPos <= 8 && yPos >= 0 && yPos <= 9) {
                grid[xPos][yPos] = res.labelName;
                
                // 自动识别红黑方 (C# 逻辑: 如果帅在上方 y < 5，则 RedSide 为 false)
                if (res.labelName.equals("r_jiang")) {
                    if (yPos < 5) {
                        redSide = false;
                    } else {
                        redSide = true;
                    }
                }
            }
        }

//        LogUtil.d(TAG, "自动识别方向: " + (redSide ? "红方在下" : "黑方在下"));
        
        // 4. 转换为 FEN (Utils.boardToFen 需要处理 redSide 镜像逻辑)
        return Utils.boardToFen(grid, redSide ? "w" : "b", redSide ? "w" : "b");
    }
}
