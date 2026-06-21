package com.example.intercept;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ConfigProvider extends ContentProvider {
    private static final String TAG = "ConfigProvider";

    @Override
    public boolean onCreate() {
        // Initialize files if they don't exist
        getRulesFile();
        getLogsFile();
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if ("shouldIntercept".equals(method)) {
            String activityName = arg;
            String packageName = extras != null ? extras.getString("packageName", "") : "";
            boolean intercept = checkShouldIntercept(activityName, packageName);
            result.putBoolean("intercept", intercept);
            return result;
        } else if ("logActivity".equals(method)) {
            String activityName = arg;
            String packageName = extras != null ? extras.getString("packageName", "") : "";
            logActivityLaunch(activityName, packageName);
            return result;
        }
        return super.call(method, arg, extras);
    }

    private File getRulesFile() {
        Context context = getContext();
        if (context == null) return null;
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        File file = new File(dir, "rules.txt");
        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    // Write default rule examples
                    try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                        bw.write("# 每行写一个要拦截的包名或Activity名（支持包含匹配）\n");
                        bw.write("# 例如：\n");
                        bw.write("com.miui.securityscan.MainActivity\n");
                        bw.write("com.miui.securityscan.MainEntryActivity\n");
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to create rules.txt", e);
            }
        }
        return file;
    }

    private File getLogsFile() {
        Context context = getContext();
        if (context == null) return null;
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        File file = new File(dir, "logs.txt");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "Failed to create logs.txt", e);
            }
        }
        return file;
    }

    private boolean checkShouldIntercept(String activityName, String packageName) {
        File file = getRulesFile();
        if (file == null || !file.exists()) return false;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                if ((activityName != null && activityName.contains(line)) || 
                    (packageName != null && packageName.contains(line))) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading rules file", e);
        }
        return false;
    }

    private synchronized void logActivityLaunch(String activityName, String packageName) {
        File file = getLogsFile();
        if (file == null) return;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            bw.write(String.format("[%s] Package: %s | Activity: %s\n", time, packageName, activityName));
        } catch (Exception e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }

    // Required ContentProvider overrides that are not used
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
