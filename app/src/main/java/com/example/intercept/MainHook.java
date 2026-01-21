package com.example.intercept;

import android.app.Activity;
import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // LSPosed 模块通常在管理界面选择目标应用，所以这里可以不进行包名过滤
        // 如果想在代码中硬编码包名，可以取消下面的注释：
        
        if (!lpparam.packageName.equals("com.miui.securitycenter")) {
            return;
        }
        

        XposedBridge.log("ActivityInterceptor: 正在拦截应用 -> " + lpparam.packageName);

        // Hook Activity 的 onCreate 方法
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

                    // 在这里定义你想要拦截的 Activity 类名（完整路径）
                    // 你可以使用 if 语句或 Switch 来匹配
                    if (activityName.equals("com.miui.securityscan.MainActivity") || 
                        activityName.contains("TargetSecretActivity")) {
                        
                        XposedBridge.log("ActivityInterceptor: 命中拦截规则 -> " + activityName);
                        
                        // 拦截并关闭
                        activity.finish();
                        
                        XposedBridge.log("ActivityInterceptor: 已成功拦截并关闭 Activity: " + activityName);
                    } else {
                        // 如果不匹配，则放行
                        XposedBridge.log("ActivityInterceptor: 放行 Activity -> " + activityName);
                    }
                }
            }
        );
    }
}
