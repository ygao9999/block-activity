package com.example.intercept;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "ActivityInterceptor";
    private XSharedPreferences prefs;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("android".equals(lpparam.packageName)) {
            hookSystemServer(lpparam);
        } else {
            hookTargetApp(lpparam);
        }
    }

    private void hookSystemServer(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.d(TAG, "全局日志系统模块已加载到 system_server");
        XposedBridge.log(TAG + ": 全局日志模块已加载到 system_server");

        Class<?> atmsClass = XposedHelpers.findClassIfExists("com.android.server.wm.ActivityTaskManagerService", lpparam.classLoader);
        if (atmsClass == null) {
            atmsClass = XposedHelpers.findClassIfExists("com.android.server.am.ActivityManagerService", lpparam.classLoader);
        }

        if (atmsClass != null) {
            XposedBridge.hookAllMethods(atmsClass, "startActivity", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    logIntent(param);
                }
            });
            XposedBridge.hookAllMethods(atmsClass, "startActivityAsUser", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    logIntent(param);
                }
            });
        }
    }

    private void logIntent(XC_MethodHook.MethodHookParam param) {
        try {
            android.content.Intent intent = null;
            for (Object arg : param.args) {
                if (arg instanceof android.content.Intent) {
                    intent = (android.content.Intent) arg;
                    break;
                }
            }

            if (intent != null && intent.getComponent() != null) {
                String packageName = intent.getComponent().getPackageName();
                String activityName = intent.getComponent().getClassName();
                
                if (prefs == null) {
                    prefs = new XSharedPreferences("com.example.intercept", "intercept_config");
                    prefs.makeWorldReadable();
                }
                prefs.reload();
                if (!prefs.getBoolean("enable_logging", true)) {
                    return;
                }

                String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                String logLine = String.format("[%s] Package: %s | Activity: %s\n", time, packageName, activityName);
                
                java.io.File logFile = new java.io.File("/data/local/tmp/intercept_logs.txt");
                try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true)) {
                    fw.write(logLine);
                }
                logFile.setReadable(true, false);
                logFile.setWritable(true, false);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in global logging", t);
        }
    }

    private void hookTargetApp(XC_LoadPackage.LoadPackageParam lpparam) {
        prefs = new XSharedPreferences("com.example.intercept", "intercept_config");
        prefs.makeWorldReadable();

        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "onCreate",
            Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    String activityName = activity.getClass().getName();
                    String packageName = lpparam.packageName;

                    if (shouldIntercept(activityName, packageName)) {
                        Log.w(TAG, "!! 拦截 !! | " + packageName + " | " + activityName);
                        XposedBridge.log(TAG + ": !! 拦截 !! | " + packageName + " | " + activityName);
                        activity.finish();
                        activity.finishAndRemoveTask();
                    }
                }
            }
        );
    }

    private boolean shouldIntercept(String activityName, String packageName) {
        prefs.reload();
        String rulesText = prefs.getString("rules_text", "");
        if (rulesText.isEmpty()) {
            return false;
        }
        String[] rules = rulesText.split("\n");
        for (String rule : rules) {
            rule = rule.trim();
            if (rule.isEmpty() || rule.startsWith("#") || rule.startsWith("//")) {
                continue;
            }
            if (activityName.contains(rule) || packageName.contains(rule)) {
                return true;
            }
        }
        return false;
    }
}
