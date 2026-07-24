# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# 防止 native 方法被混淆
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep EngineHelper and its callback methods for JNI
-keep class com.yieye.xiangqi.EngineHelper {
    public *;
}

# Workaround for Xiaomi/MIUI ILocationPolicyManager crash
-keep class android.location.** { *; }
-dontwarn android.location.**

# Keep PowerKeeper related hooks if any
-keep class com.miui.powerkeeper.** { *; }
-dontwarn com.miui.powerkeeper.**

# WorkManager / Room
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.background.systemalarm.SystemAlarmService { *; }
-keep class androidx.work.impl.background.systemjob.SystemJobService { *; }
-keep class androidx.work.impl.foreground.SystemForegroundService { *; }
-keep class androidx.work.impl.diagnostics.DiagnosticsReceiver { *; }
-keep class androidx.work.impl.utils.ForceStopRunnable$BroadcastReceiver { *; }
-keep class androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryChargingProxy { *; }
-keep class androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryNotLowProxy { *; }
-keep class androidx.work.impl.background.systemalarm.ConstraintProxy$StorageNotLowProxy { *; }
-keep class androidx.work.impl.background.systemalarm.ConstraintProxy$NetworkStateProxy { *; }
-keep class androidx.work.impl.background.systemalarm.RescheduleReceiver { *; }
-keep class androidx.work.impl.background.systemalarm.ConstraintProxyUpdateReceiver { *; }