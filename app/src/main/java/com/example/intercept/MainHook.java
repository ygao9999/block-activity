package com.example.intercept;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "ActivityInterceptor";

    // 全局日志专用单线程池（复用线程，避免频繁创建线程的开销）
    private static final ExecutorService LOG_WRITER = Executors.newSingleThreadExecutor();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("android".equals(lpparam.packageName)) {
            // ===== 全局日志（只看不摸）=====
            hookSystemServerSafe(lpparam);
            return;
        }

        // ===== 目标 App 拦截（精准狙杀）=====
        Log.d(TAG, "模块已加载到包: " + lpparam.packageName);
        XposedBridge.log(TAG + ": 模块已加载到包: " + lpparam.packageName);
        hookTargetApp(lpparam);
    }

    // ==========================================
    // 第一层：system_server 全局安全日志
    // 设计原则：afterHookedMethod + 异步 + 三层 try-catch
    // ==========================================

    private void hookSystemServerSafe(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log(TAG + ": 全局安全日志模块加载到 system_server");

        Class<?> atmsClass = XposedHelpers.findClassIfExists(
            "com.android.server.wm.ActivityTaskManagerService", lpparam.classLoader);
        if (atmsClass == null) {
            atmsClass = XposedHelpers.findClassIfExists(
                "com.android.server.am.ActivityManagerService", lpparam.classLoader);
        }
        if (atmsClass == null) {
            XposedBridge.log(TAG + ": 未找到 ATMS/AMS 类，全局日志不可用");
            return;
        }

        XposedBridge.log(TAG + ": 成功找到调度类: " + atmsClass.getName());

        // 关键安全设计：使用 afterHookedMethod
        // Activity 启动已经完成后才记录，绝不阻塞或干扰启动流程
        XC_MethodHook safeLogHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    safeLogStartActivity(param);
                } catch (Throwable ignored) {
                    // 吞掉一切异常，绝不让 system_server 崩溃
                }
            }
        };

        // 每个方法独立 try-catch，某个方法不存在不影响其他
        try { XposedBridge.hookAllMethods(atmsClass, "startActivity", safeLogHook); } catch (Throwable ignored) {}
        try { XposedBridge.hookAllMethods(atmsClass, "startActivityAsUser", safeLogHook); } catch (Throwable ignored) {}
    }

    private void safeLogStartActivity(XC_MethodHook.MethodHookParam param) {
        // 安全提取 Intent：逐个参数独立 try-catch，避免触发私有 Parcelable 反序列化
        android.content.Intent intent = null;
        for (int i = 0; i < param.args.length; i++) {
            try {
                if (param.args[i] instanceof android.content.Intent) {
                    intent = (android.content.Intent) param.args[i];
                    break;
                }
            } catch (Throwable ignored) {
                continue; // 这个参数有毒，跳过
            }
        }
        if (intent == null) return;

        // 安全读取 ComponentName（独立 try-catch）
        String pkg;
        String cls;
        try {
            android.content.ComponentName comp = intent.getComponent();
            if (comp == null) return; // 隐式 Intent，不记录
            pkg = comp.getPackageName();
            cls = comp.getClassName();
        } catch (Throwable ignored) {
            return; // 读取失败，放弃这条记录（绝不冒险）
        }
        if (pkg == null || cls == null) return;

        // 异步写入日志文件（单线程池，零阻塞 Binder 线程）
        final String fpkg = pkg;
        final String fcls = cls;
        LOG_WRITER.execute(() -> {
            try {
                // 尝试读取暂停开关（读不到就默认开启）
                boolean loggingEnabled = true;
                try {
                    XSharedPreferences prefs = new XSharedPreferences("com.example.intercept", "intercept_config");
                    prefs.makeWorldReadable();
                    prefs.reload();
                    loggingEnabled = prefs.getBoolean("enable_logging", true);
                } catch (Throwable ignored) {}

                if (!loggingEnabled) return;

                String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.getDefault()).format(new java.util.Date());
                String logLine = String.format("[%s] Package: %s | Activity: %s\n", time, fpkg, fcls);

                java.io.File logFile = new java.io.File("/data/local/tmp/intercept_logs.txt");
                try (java.io.FileWriter fw = new java.io.FileWriter(logFile, true)) {
                    fw.write(logLine);
                }
            } catch (Throwable t) {
                // 写入失败时记录到 Xposed 日志便于排查，但绝不影响系统运行
                XposedBridge.log(TAG + ": 全局日志写入失败: " + t.getMessage());
            }
        });
    }

    // ==========================================
    // 第二层：目标 App 精确拦截（通过 ContentProvider）
    // ==========================================

    private void hookTargetApp(XC_LoadPackage.LoadPackageParam lpparam) {
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

                    if (shouldIntercept(activity, activityName, packageName)) {
                        Log.w(TAG, "!! 拦截 !! | " + packageName + " | " + activityName);
                        XposedBridge.log(TAG + ": !! 拦截 !! | " + packageName + " | " + activityName);
                        
                        // 核心修复：阻止原 Activity 的 onCreate 继续执行（否则微信会在后台继续初始化播放器）
                        param.setResult(null);
                        
                        enforceInterception(activity);
                    }
                }
            }
        );
    }

    private void enforceInterception(Activity activity) {
        // 1. 强制回到桌面，打断用户视觉和焦点
        try {
            android.content.Intent homeIntent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            homeIntent.addCategory(android.content.Intent.CATEGORY_HOME);
            homeIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(homeIntent);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 回到桌面失败: " + t.getMessage());
        }

        // 2. 强制销毁当前页面
        try {
            activity.finishAndRemoveTask();
            activity.finish();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": finish失败: " + t.getMessage());
        }

        // 3. 强制抢占音频焦点，打断视频号可能在后台播放的声音
        try {
            android.media.AudioManager am = (android.media.AudioManager) activity.getSystemService(android.content.Context.AUDIO_SERVICE);
            if (am != null) {
                // 申请最高级别的独占音频焦点，迫使其他播放器静音或暂停
                am.requestAudioFocus(null, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 音频抢占失败: " + t.getMessage());
        }
    }

    private boolean shouldIntercept(Activity activity, String activityName, String packageName) {
        // 0. 终极大招：从系统的 Settings.Global 里拿数据。这玩意存在内存里，不需要任何文件权限，无视一切 SELinux 和隔离！
        try {
            String settingsRules = android.provider.Settings.Global.getString(activity.getContentResolver(), "activity_intercept_rules");
            if (settingsRules != null && !settingsRules.isEmpty()) {
                XposedBridge.log(TAG + ": 成功从 Settings.Global 读取规则: " + settingsRules);
                String[] rules = settingsRules.split(",");
                for (String rule : rules) {
                    rule = rule.trim();
                    if (rule.isEmpty() || rule.startsWith("#") || rule.startsWith("//")) {
                        continue;
                    }
                    if (activityName.contains(rule) || packageName.contains(rule)) {
                        return true;
                    }
                }
                return false; // 如果 Settings 里有有效数据，即使没匹配上，也直接返回 false，不用往下走了
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Settings.Global 查询失败: " + t.getMessage());
        }

        // 1. 备选方案1：通过 ContentProvider 跨进程查询，完美绕过 SELinux 限制
        try {
            android.net.Uri uri = android.net.Uri.parse("content://com.example.intercept.provider");
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("packageName", packageName);
            android.os.Bundle result = activity.getContentResolver().call(uri, "shouldIntercept", activityName, extras);
            if (result != null) {
                return result.getBoolean("intercept", false);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Provider 查询失败: " + t.getMessage());
        }

        // 2. 备用方案：直接读文件（可能被目标 App 的 SELinux 拦截）
        try {
            String rulesText = "";
            java.io.File rulesFile = new java.io.File("/data/local/tmp/intercept_rules.txt");
            if (rulesFile.exists() && rulesFile.canRead()) {
                StringBuilder sb = new StringBuilder();
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(rulesFile));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                br.close();
                rulesText = sb.toString();
            } else {
                XSharedPreferences prefs = new XSharedPreferences("com.example.intercept", "intercept_config");
                prefs.makeWorldReadable();
                prefs.reload();
                rulesText = prefs.getString("rules_text", "");
            }
            
            if (rulesText.isEmpty()) {
                XposedBridge.log(TAG + ": 警告1 - 读取规则失败，激活硬编码保底！");
                return isHardcodedFallback(activityName, packageName);
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
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 警告2 - 读取规则失败，激活硬编码保底！异常: " + android.util.Log.getStackTraceString(t));
            return isHardcodedFallback(activityName, packageName);
        }
    }

    private boolean isHardcodedFallback(String activityName, String packageName) {
        return activityName.contains("com.miui.securityscan.MainActivity") ||
               activityName.contains("com.miui.securityscan.MainEntryActivity");
    }
}
