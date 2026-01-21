package com.example.intercept;

import android.app.Activity;
import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import android.util.Log;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "ActivityInterceptor";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 使用 Log.d 打印，可以在 Logcat 中通过 TAG "ActivityInterceptor" 过滤
        Log.d(TAG, "模块已加载到包: " + lpparam.packageName);
        XposedBridge.log(TAG + ": 模块已加载到包: " + lpparam.packageName);

        // 如果你勾选了多个应用，这里可以用 if 过滤
        // 为了调试，建议初次尝试时先注释掉过滤逻辑，或者确保 packageName 匹配无误
        /*
        if (!lpparam.packageName.equals("com.miui.securitycenter")) {
            return;
        }
        */

        // Hook 所有 Activity 的 onCreate
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
                    
                    Log.d(TAG, "检测到 Activity 启动: " + activityName);
                    XposedBridge.log(TAG + ": 检测到 Activity 启动: " + activityName);

                    // 检查类名是否包含目标字符串
                    if (activityName.contains("com.miui.securityscan.MainActivity") || 
                        activityName.contains("securitycenter")) {
                        
                        Log.w(TAG, "!! 命中拦截规则 !! -> " + activityName);
                        XposedBridge.log(TAG + ": !! 命中拦截规则 !! -> " + activityName);
                        
                        // 拦截
                        activity.finish();
                    }
                }
            }
        );
    }
}
