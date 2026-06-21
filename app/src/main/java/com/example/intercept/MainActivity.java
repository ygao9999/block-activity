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

 
}
