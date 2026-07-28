package com.yieye.xiangqi;

import android.graphics.Bitmap;

import java.util.List;

/**
 * 棋盘/棋子检测器的统一接口，{@link YoloV5Detector} 和 {@link YoloV11Detector} 都实现了它，
 * 方便上层代码（{@link ChessBoardParser}）通过一个开关变量在两个模型之间切换，无需改动调用逻辑。
 */
public interface ChessDetector {
    List<YoloResult> detect(Bitmap bitmap) throws Exception;
}
