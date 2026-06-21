package com.example.intercept;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ConfigProvider extends ContentProvider {
    private static final String TAG = "ConfigProvider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("logActivity".equals(method)) {
            String activityName = arg;
            String packageName = extras != null ? extras.getString("packageName", "") : "";
            logActivityLaunch(activityName, packageName);
        }
        return new Bundle();
    }

    private File getLogsFile() {
        Context context = getContext();
        if (context == null) return null;
        File dir = context.getFilesDir();
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

    private synchronized void logActivityLaunch(String activityName, String packageName) {
        Context context = getContext();
        if (context == null) return;
        
        boolean isLoggingEnabled = context.getSharedPreferences("intercept_config", Context.MODE_PRIVATE)
                .getBoolean("enable_logging", true);
        if (!isLoggingEnabled) {
            return;
        }

        File file = getLogsFile();
        if (file == null) return;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            bw.write(String.format("[%s] Package: %s | Activity: %s\n", time, packageName, activityName));
        } catch (Exception e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }

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
