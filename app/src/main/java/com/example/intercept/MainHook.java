package com.example.intercept;

import android.app.Activity;
import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import android.provider.Telephony;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.net.Uri;
import java.util.List;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "ActivityInterceptor";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 使用 Log.d 打印，可以在 Logcat 中通过 TAG "ActivityInterceptor" 过滤
        Log.d(TAG, "模块已加载到包: " + lpparam.packageName);
        XposedBridge.log(TAG + ": 模块已加载到包: " + lpparam.packageName);

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
                    String packageName = lpparam.packageName;

                    // Log activity launch via ConfigProvider
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
                        Log.e(TAG, "Error logging activity via provider", e);
                    }

                    Log.d(TAG, "检测到 Activity 启动: " + activityName);
                    XposedBridge.log(TAG + ": 检测到 Activity 启动: " + activityName);                    

                    // Query interception status via ConfigProvider
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
                        Log.e(TAG, "Error querying intercept rules via provider", e);
                        // Fallback to local hardcoded check if provider fails
                        intercept = shouldInterceptFallback(activityName, packageName);
                    }

                    if (intercept) {
                        Log.w(TAG, "!! 拦截触发 (onCreate 之前) !! -> " + activityName);
                        XposedBridge.log(TAG + ": !! 拦截触发 (onCreate 之前) !! -> " + activityName);
                        activity.finish();
                        activity.finishAndRemoveTask();
                    }
                }
            }
        );
    }

    private boolean shouldInterceptFallback(String activityName, String packageName) {
        return activityName.contains("com.miui.securityscan.MainActivity") || 
               activityName.contains("com.miui.securityscan.MainEntryActivity");
    }
}
