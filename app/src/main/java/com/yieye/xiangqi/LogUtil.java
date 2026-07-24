package com.yieye.xiangqi;

import android.util.Log;

public class LogUtil {
    // 使用项目自带的 BuildConfig 来判断是否是 Debug 版本
    private static final boolean DEBUG = BuildConfig.DEBUG;

    public static void d(String tag, String msg) {
        if (DEBUG) Log.d(tag, msg);
    }

    public static void i(String tag, String msg) {
        if (DEBUG) Log.i(tag, msg);
    }

    public static void w(String tag, String msg) {
        if (DEBUG) Log.w(tag, msg);
    }

    public static void e(String tag, String msg) {
        if (DEBUG) Log.e(tag, msg);
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (DEBUG) Log.e(tag, msg, tr);
    }
}
