package com.example.intercept;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "ActivityInterceptor";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 全面放弃 system_server 全局 Hook，只在具体 App 进程内拦截，绝对安全！
        if ("android".equals(lpparam.packageName)) {
            return;
        }
        
        Log.d(TAG, "模块已加载到包: " + lpparam.packageName);
        XposedBridge.log(TAG + ": 模块已加载到包: " + lpparam.packageName);

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

                    // 1. 发起日志记录（异步，不卡主线程）
                    new Thread(() -> {
                        try {
                            Bundle extras = new Bundle();
                            extras.putString("packageName", packageName);
                            activity.getContentResolver().call(
                                Uri.parse("content://com.example.intercept.provider"),
                                "logActivity",
                                activityName,
                                extras
                            );
                        } catch (Exception e) {
                            Log.e(TAG, "Error async logging activity via provider", e);
                        }
                    }).start();

                    // 2. 发起拦截检查（同步，必须拦截成功）
                    boolean intercept = false;
                    try {
                        Bundle extras = new Bundle();
                        extras.putString("packageName", packageName);
                        Bundle result = activity.getContentResolver().call(
                            Uri.parse("content://com.example.intercept.provider"),
                            "shouldIntercept",
                            activityName,
                            extras
                        );
                        if (result != null) {
                            intercept = result.getBoolean("intercept", false);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error querying provider for rules", e);
                        // 终极防弹保底：如果 ContentProvider 被小米系统强行断开，激活最原始的硬编码规则！
                        XposedBridge.log(TAG + ": 警告 - ContentProvider 查询失败，激活硬编码保底！");
                        if (activityName.contains("com.miui.securityscan.MainActivity") || 
                            activityName.contains("com.miui.securityscan.MainEntryActivity") || 
                            packageName.contains("securitycenter")) {
                            intercept = true;
                        }
                    }

                    if (intercept) {
                        Log.w(TAG, "!! 拦截 !! | " + packageName + " | " + activityName);
                        XposedBridge.log(TAG + ": !! 拦截 !! | " + packageName + " | " + activityName);
                        activity.finish();
                        activity.finishAndRemoveTask();
                    }
                }
            }
        );
    }
}
