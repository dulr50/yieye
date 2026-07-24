package com.yieye.xiangqi;

import android.graphics.RectF;

public class YoloResult {
    public RectF rect;
    public float score;
    public int labelId;
    public String labelName;

    public YoloResult(RectF rect, float score, int labelId, String labelName) {
        this.rect = rect;
        this.score = score;
        this.labelId = labelId;
        this.labelName = labelName;
    }
}
