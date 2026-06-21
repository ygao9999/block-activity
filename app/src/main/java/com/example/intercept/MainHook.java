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
            Log.d(TAG, "成功找到核心调度类: " + atmsClass.getName());
            XposedBridge.log(TAG + ": 成功找到核心调度类: " + atmsClass.getName());
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
            // 兼容部分小米机型
            XposedBridge.hookAllMethods(atmsClass, "startActivityAndWait", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    logIntent(param);
                }
            });
        } else {
            XposedBridge.log(TAG + ": 致命错误，未找到 ATMS/AMS 类！");
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

            if (intent != null) {
                String packageName = "unknown";
                String activityName = "unknown";
                
                if (intent.getComponent() != null) {
                    packageName = intent.getComponent().getPackageName();
                    activityName = intent.getComponent().getClassName();
                } else {
                    packageName = intent.getPackage() != null ? intent.getPackage() : "implicit";
                    activityName = intent.getAction() != null ? intent.getAction() : "implicit";
                }

                // 打印到 Xposed 日志用于调试验证
                XposedBridge.log(TAG + ": ATMS 捕获到启动请求 -> " + packageName + " | " + activityName);
                
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
                
                // 改用 /data/system/ 因为 system_server 写入 /data/local/tmp 可能被 SELinux 拒绝
                java.io.File logFile = new java.io.File("/data/system/intercept_logs.txt");
                try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true)) {
                    fw.write(logLine);
                }
                logFile.setReadable(true, false);
                logFile.setWritable(true, false);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error in global logging: " + android.util.Log.getStackTraceString(t));
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
        
        // 终极防弹保底机制：如果因为小米底层 SELinux 拦截导致 XSharedPreferences 读取失败（即为空）
        // 则强制使用您一开始手写的硬编码规则，保证拦截绝对不失效！
        if (rulesText.isEmpty()) {
            rulesText = "com.miui.securityscan.MainActivity\ncom.miui.securityscan.MainEntryActivity\nsecuritycenter";
            XposedBridge.log(TAG + ": 警告 - 无法读取动态规则，已自动启用硬编码保底拦截！");
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
