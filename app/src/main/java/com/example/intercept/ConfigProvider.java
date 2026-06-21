package com.example.intercept;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ConfigProvider extends ContentProvider {
    private static final String TAG = "ActivityInterceptor";
    private SharedPreferences prefs;

    @Override
    public boolean onCreate() {
        if (getContext() != null) {
            prefs = getContext().getSharedPreferences("intercept_config", Context.MODE_PRIVATE);
        }
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (prefs == null && getContext() != null) {
            prefs = getContext().getSharedPreferences("intercept_config", Context.MODE_PRIVATE);
        }

        if ("shouldIntercept".equals(method)) {
            boolean intercept = false;
            if (prefs != null) {
                String rulesText = prefs.getString("rules_text", "");
                String activityName = arg;
                String packageName = extras != null ? extras.getString("packageName", "") : "";
                
                if (rulesText.isEmpty()) {
                    // Fallback in provider just in case
                    rulesText = "com.miui.securityscan.MainActivity\ncom.miui.securityscan.MainEntryActivity";
                }

                String[] rules = rulesText.split("\n");
                for (String rule : rules) {
                    rule = rule.trim();
                    if (!rule.isEmpty() && !rule.startsWith("#") && !rule.startsWith("//")) {
                        if (activityName != null && activityName.contains(rule)) {
                            intercept = true;
                            break;
                        }
                        if (packageName != null && packageName.contains(rule)) {
                            intercept = true;
                            break;
                        }
                    }
                }
            }
            result.putBoolean("intercept", intercept);
        } else if ("logActivity".equals(method)) {
            if (prefs != null && prefs.getBoolean("enable_logging", true)) {
                String activityName = arg;
                String packageName = extras != null ? extras.getString("packageName", "") : "";
                logToFile(packageName, activityName);
            }
        }
        return result;
    }
    
    private void logToFile(String packageName, String activityName) {
        if (getContext() == null) return;
        
        // Use internal storage for bulletproof writing without SELinux block
        File file = new File(getContext().getFilesDir(), "logs.txt");
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String logLine = String.format("[%s] Package: %s | Activity: %s\n", time, packageName, activityName);
            
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(logLine);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }

    // --- Unused standard provider methods ---
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override
    public String getType(Uri uri) { return null; }
    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
