package com.yieye.xiangqi;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ChessLogic";
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private ImageView debugImageView;
    private TextView resultTextView;
    private android.view.View permissionLayout;
    private Button btnStartService;
    private android.widget.Spinner spinnerDepth;
    private static final String PREFS_NAME = "ChessPrefs";
    private static final String KEY_DEPTH = "calc_depth";

    private BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String displayStr = intent.getStringExtra("displayStr");
            if (displayStr != null) {
                runOnUiThread(() -> resultTextView.append("\n" + displayStr));
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        debugImageView = findViewById(R.id.debugImageView);
        resultTextView = findViewById(R.id.resultTextView);
        permissionLayout = findViewById(R.id.permissionLayout);
        Button btnOpenPermission = findViewById(R.id.btnOpenPermission);
        btnStartService = findViewById(R.id.btnStartService);
        spinnerDepth = findViewById(R.id.spinnerDepth);
        Button btnBatterySetting = findViewById(R.id.btnBatterySetting);

        initDepthSpinner();

        btnBatterySetting.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });

        btnOpenPermission.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 100);
        });
//“本应用使用屏幕捕获功能识别象棋棋盘，仅在用户主动开启时运行，不会收集或上传个人数据。”
        btnStartService.setOnClickListener(v -> {
            if (isServiceRunning(AnalysisService.class)) {
                stopService(new Intent(this, AnalysisService.class));
                btnStartService.setText(R.string.start_analysis);
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                checkFloatWindowPermission();
                return;
            }
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        });

        // 检查悬浮窗权限
        updatePermissionBanner();
        updateServiceButtonState();

        // 注册广播接收器
        IntentFilter filter = new IntentFilter("com.example.CHESS_RESULT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resultReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(resultReceiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionBanner();
        updateServiceButtonState();
    }

    private void updateServiceButtonState() {
        if (isServiceRunning(AnalysisService.class)) {
            btnStartService.setText(R.string.stop_analysis);
        } else {
            btnStartService.setText(R.string.start_analysis);
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void updatePermissionBanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                permissionLayout.setVisibility(android.view.View.GONE);
            } else {
                permissionLayout.setVisibility(android.view.View.VISIBLE);
            }
        }
    }

    private void initDepthSpinner() {
        java.util.List<Integer> depths = new java.util.ArrayList<>();
        for (int i = 6; i <= 30; i += 2) {
            depths.add(i);
        }
        android.widget.ArrayAdapter<Integer> adapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, depths);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDepth.setAdapter(adapter);

        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedDepth = prefs.getInt(KEY_DEPTH, 14);
        int selection = depths.indexOf(savedDepth);
        if (selection >= 0) {
            spinnerDepth.setSelection(selection);
        }

        spinnerDepth.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                int depth = (int) parent.getItemAtPosition(position);
                prefs.edit().putInt(KEY_DEPTH, depth).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK) {
            Intent serviceIntent = new Intent(this, AnalysisService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            serviceIntent.putExtra("depth", (int) spinnerDepth.getSelectedItem());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            btnStartService.setText(R.string.stop_analysis);
            Toast.makeText(this, R.string.analysis_service_started, Toast.LENGTH_SHORT).show();
            moveTaskToBack(true); // 返回桌面，方便用户打开棋类APP
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(resultReceiver);
        } catch (Exception e) {
            // ignore
        }
    }

    private void checkFloatWindowPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.grant_float_permission_tip, Toast.LENGTH_LONG).show();
                updatePermissionBanner();
            }
        }
    }

    private void drawDetections(Bitmap bitmap, List<YoloResult> results) {
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setTextSize(40);

        for (YoloResult res : results) {
            if (res.labelName.equals("board")) {
                paint.setColor(Color.GREEN);
            } else {
                paint.setColor(Color.RED);
            }
            canvas.drawRect(res.rect, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(res.labelName, res.rect.left, res.rect.top, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
        debugImageView.setImageBitmap(mutableBitmap);
    }
}