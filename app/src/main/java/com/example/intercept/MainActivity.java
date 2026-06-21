package com.example.intercept;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private static final String TAG = "ActivityInterceptor";
    private static final String PREFS_NAME = "intercept_config";

    private EditText etRules;
    private TextView tvLogs;
    private SharedPreferences prefs;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // MODE_WORLD_READABLE 使 XSharedPreferences 可在 Hook 模块中直接本地读取
        // LSPosed 对此做了兼容处理
        try {
            prefs = getSharedPreferences(PREFS_NAME, MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Log.w(TAG, "MODE_WORLD_READABLE 不可用，回退到 MODE_PRIVATE", e);
        }

        // Secret Code Features
        Button btnRegister = findViewById(R.id.btn_register_secret_code);
        btnRegister.setOnClickListener(v -> registerSecretCodeReceiver());

        Button btnTrigger = findViewById(R.id.btn_trigger_secret_code);
        btnTrigger.setOnClickListener(v -> triggerSecretCode("6776799"));

        // 拦截规则
        etRules = findViewById(R.id.et_rules);
        tvLogs = findViewById(R.id.tv_logs);

        Button btnSaveRules = findViewById(R.id.btn_save_rules);
        btnSaveRules.setOnClickListener(v -> saveRules());

        Button btnRefreshLogs = findViewById(R.id.btn_refresh_logs);
        btnRefreshLogs.setOnClickListener(v -> loadLogs());

        Button btnClearLogs = findViewById(R.id.btn_clear_logs);
        btnClearLogs.setOnClickListener(v -> clearLogs());

        loadRules();
        loadLogs();
    }

    private void loadRules() {
        String rulesText = prefs.getString("rules_text", "");
        etRules.setText(rulesText);
    }

    private void saveRules() {
        String rulesText = etRules.getText().toString();
        prefs.edit().putString("rules_text", rulesText).apply();
        Toast.makeText(this, "规则已保存，重启目标应用后生效", Toast.LENGTH_SHORT).show();
    }

    private File getLogsFile() {
        File dir = getFilesDir();
        File file = new File(dir, "logs.txt");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (Exception ignored) {}
        }
        return file;
    }

    private void loadLogs() {
        new Thread(() -> {
            File file = getLogsFile();
            if (file == null || !file.exists()) {
                runOnUiThread(() -> tvLogs.setText("暂无日志文件"));
                return;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading logs", e);
                sb.append("读取日志失败: ").append(e.getMessage());
            }
            String logsText = sb.toString().trim();
            runOnUiThread(() -> {
                tvLogs.setText(logsText.isEmpty() ? "暂无日志" : logsText);
            });
        }).start();
    }

    private void clearLogs() {
        new Thread(() -> {
            File file = getLogsFile();
            if (file != null) {
                try (FileWriter fw = new FileWriter(file, false)) {
                    // overwrite with empty content
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing logs", e);
                }
            }
            runOnUiThread(() -> {
                tvLogs.setText("暂无日志");
                Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void triggerSecretCode(String code) {
        String action;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action = TelephonyManager.ACTION_SECRET_CODE;
        } else {
            action = "android.provider.Telephony.SECRET_CODE";
        }

        String cmd = "am broadcast -a " + action + " -d android_secret_code://" + code;

        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            Log.d(TAG, "Sent Secret Code Broadcast via Root: " + cmd);
            Toast.makeText(this, "Triggering via Root...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute root command, trying standard broadcast", e);
            Intent intent = new Intent(action, android.net.Uri.parse("android_secret_code://" + code));
            sendBroadcast(intent);
        }
    }

    private void registerSecretCodeReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intentFilter.addAction(TelephonyManager.ACTION_SECRET_CODE);
        } else {
            // noinspection InlinedApi
            intentFilter.addAction(Telephony.Sms.Intents.SECRET_CODE_ACTION);
        }
        intentFilter.addDataAuthority("6776799", null);
        intentFilter.addDataScheme("android_secret_code");

        Log.d(TAG, "registering secret code receiver...");

        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    dispatchSecretCodeReceive(context, intent);
                }
            };

            int flags = Context.RECEIVER_EXPORTED;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, intentFilter, "android.permission.CONTROL_INCALL_EXPERIENCE", null, flags);
            } else {
                registerReceiver(receiver, intentFilter, "android.permission.CONTROL_INCALL_EXPERIENCE", null);
            }

            Log.d(TAG, "registered secret code receiver");
            Toast.makeText(this, "Receiver Registered", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to register receiver", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void dispatchSecretCodeReceive(Context context, Intent intent) {
        String code = intent.getData() != null ? intent.getData().getHost() : "unknown";
        Log.d(TAG, "Secret code received: " + code);
        Toast.makeText(context, "Secret Code Received: " + code, Toast.LENGTH_LONG).show();
    }
}
