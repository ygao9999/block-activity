package com.example.intercept;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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

        // 拦截规则
        etRules = findViewById(R.id.et_rules);
        tvLogs = findViewById(R.id.tv_logs);
        android.widget.Switch switchLogging = findViewById(R.id.switch_enable_logging);

        Button btnSaveRules = findViewById(R.id.btn_save_rules);
        btnSaveRules.setOnClickListener(v -> saveRules());

        Button btnRefreshLogs = findViewById(R.id.btn_refresh_logs);
        btnRefreshLogs.setOnClickListener(v -> loadLogs());

        Button btnClearLogs = findViewById(R.id.btn_clear_logs);
        btnClearLogs.setOnClickListener(v -> clearLogs());

        // 初始化日志开关
        boolean isLoggingEnabled = prefs.getBoolean("enable_logging", true);
        switchLogging.setChecked(isLoggingEnabled);
        switchLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("enable_logging", isChecked).apply();
            fixPrefsPermissions();
            String msg = isChecked ? "日志记录已开启" : "日志记录已暂停";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        initGlobalLogFile();
        loadRules();
        loadLogs();
    }

    private void loadRules() {
        String rulesText = prefs.getString("rules_text", "");
        
        // 如果 SharedPreferences 里没有规则，尝试从旧版本的 rules.txt 迁移，以免丢失默认规则
        if (rulesText.isEmpty()) {
            File oldRulesFile = new File(getFilesDir(), "rules.txt");
            if (oldRulesFile.exists()) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new FileReader(oldRulesFile))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    rulesText = sb.toString().trim();
                    // 迁移保存到 SharedPreferences
                    prefs.edit().putString("rules_text", rulesText).apply();
                } catch (Exception e) {
                    Log.e(TAG, "Error migrating old rules", e);
                }
            }
            
            // 如果迁移后依然是空的（或者根本没有旧文件），就加上原版预置的两条规则
            if (rulesText.isEmpty()) {
                rulesText = "com.miui.securityscan.MainActivity\ncom.miui.securityscan.MainEntryActivity";
                prefs.edit().putString("rules_text", rulesText).apply();
            }
        }
        
        etRules.setText(rulesText);
        fixPrefsPermissions();
    }

    private void fixPrefsPermissions() {
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod 666 /data/data/com.example.intercept/shared_prefs/" + PREFS_NAME + ".xml"}).waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Error fixing prefs permissions", e);
            }
        }).start();
    }

    private void initGlobalLogFile() {
        // 用 Root 权限预先创建全局日志文件，确保 system_server 可写入
        new Thread(() -> {
            try {
                String cmd = "touch /data/local/tmp/intercept_logs.txt && chown 1000:1000 /data/local/tmp/intercept_logs.txt && chmod 666 /data/local/tmp/intercept_logs.txt && (chcon u:object_r:system_data_file:s0 /data/local/tmp/intercept_logs.txt || true)";
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                p.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing global log file", e);
            }
        }).start();
    }

    private void saveRules() {
        String rulesText = etRules.getText().toString();
        prefs.edit().putString("rules_text", rulesText).apply();
        fixPrefsPermissions();
        Toast.makeText(this, "规则已保存，重启目标应用后生效", Toast.LENGTH_SHORT).show();
    }

    private void loadLogs() {
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            // 优先读取全局日志（system_server 写入的）
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat /data/local/tmp/intercept_logs.txt 2>/dev/null"});
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                p.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Error reading global logs", e);
            }

            // 如果全局日志为空，尝试读取本地日志（ContentProvider 写入的备用日志）
            if (sb.length() == 0) {
                File localLog = new File(getFilesDir(), "logs.txt");
                if (localLog.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(localLog))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading local logs", e);
                    }
                }
            }

            String logsText = sb.toString().trim();
            runOnUiThread(() -> {
                tvLogs.setText(logsText.isEmpty() ? "暂无日志" : logsText);
            });
        }).start();
    }

    private void clearLogs() {
        new Thread(() -> {
            // 清除全局日志
            try {
                String cmd = "echo -n > /data/local/tmp/intercept_logs.txt && chown 1000:1000 /data/local/tmp/intercept_logs.txt && chmod 666 /data/local/tmp/intercept_logs.txt && (chcon u:object_r:system_data_file:s0 /data/local/tmp/intercept_logs.txt || true)";
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                p.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Error clearing global logs", e);
            }
            // 清除本地日志
            File localLog = new File(getFilesDir(), "logs.txt");
            if (localLog.exists()) {
                try (FileWriter fw = new FileWriter(localLog, false)) {
                    // overwrite with empty
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing local logs", e);
                }
            }
            runOnUiThread(() -> {
                tvLogs.setText("暂无日志");
                Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
}
