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
        Log.d(TAG, "模块已加载到包: " + lpparam.packageName);
        XposedBridge.log(TAG + ": 模块已加载到包: " + lpparam.packageName);

        // 本地直接读取主 App 的 SharedPreferences 配置文件，无任何跨进程通信
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

                    // 日志直接写入 Logcat 和 Xposed 日志，零额外开销
                    Log.d(TAG, "Activity启动 | " + packageName + " | " + activityName);
                    XposedBridge.log(TAG + ": Activity启动 | " + packageName + " | " + activityName);

                    // 异步调用 ContentProvider 写日志，全天持久化且不卡主线程
                    new Thread(() -> {
                        try {
                            Bundle extras = new Bundle();
                            extras.putString("packageName", packageName);
                            activity.getContentResolver().call(
                                android.net.Uri.parse("content://com.example.intercept.provider"),
                                "logActivity",
                                activityName,
                                extras
                            );
                        } catch (Exception e) {
                            Log.e(TAG, "Error async logging activity", e);
                        }
                    }).start();

                    // 本地读取规则判断是否拦截
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
