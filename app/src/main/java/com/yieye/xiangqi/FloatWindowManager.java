package com.yieye.xiangqi;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatWindowManager {
    private static FloatWindowManager instance;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private View floatView;
    private TextView moveTextView;
    private Context context;

    private FloatWindowManager(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized FloatWindowManager getInstance(Context context) {
        if (instance == null) {
            instance = new FloatWindowManager(context);
        }
        return instance;
    }

    public void show() {
        if (floatView != null) return;

        floatView = LayoutInflater.from(context).inflate(R.layout.float_window_layout, null);
        moveTextView = floatView.findViewById(R.id.float_move_text);

        layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.x = 100;
        layoutParams.y = 100;

        floatView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        layoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        layoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatView, layoutParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatView, layoutParams);
    }

    public void updateMove(String move) {
        if (moveTextView != null) {
            moveTextView.post(() -> moveTextView.setText(move));
        }
    }

    public void hide() {
        if (floatView != null) {
            windowManager.removeView(floatView);
            floatView = null;
        }
    }
}
