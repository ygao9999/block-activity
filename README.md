# Activity Interceptor (LSPosed Module)

这是一个简单的 LSPosed 模块，用于拦截并关闭目标应用的所有 Activity。

## 功能
- 自动 Hook `android.app.Activity.onCreate`。
- 在 Activity 创建时立即调用 `finish()`，使其无法正常显示。
- 在 LSPosed 日志中输出拦截记录。

## 使用说明
1. 使用 Android Studio 打开此项目。
2. 编译并生成 APK。
3. 在 Android 设备上安装 APK。
4. 打开 LSPosed 管理器，启用此模块。
5. **重要：** 在 LSPosed 模块设置中，勾选你想要拦截的目标应用。
6. 重启目标应用，你会发现其所有 Activity 都会在启动时被立即关闭。

## 核心代码说明
拦截逻辑位于 `app/src/main/java/com/example/intercept/MainHook.java`：
- 通过 `XposedHelpers.findAndHookMethod` 监听所有 `Activity` 的 `onCreate` 生命周期。
- 在 `beforeHookedMethod` 中执行 `activity.finish()`。

## 注意事项
- 此模块对系统级 Activity 也要谨慎使用（如果你在 LSPosed 中勾选了系统框架）。
- 某些 Activity 可能会在 `onCreate` 之前进行某些初始化，但 `finish()` 通常足以阻止 UI 展示。
