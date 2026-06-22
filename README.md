# Activity Interceptor (LSPosed Module)

这是一个强大的 LSPosed 模块，用于精准拦截并关闭目标应用的指定 Activity，同时提供全局 Activity 启动日志记录功能，方便开发者和高级用户抓取和分析应用的 Activity 调度情况。

## 核心功能

1. **精准 Activity 拦截**：
   - 自动 Hook 目标应用的 `android.app.Activity.onCreate`。
   - 在 Activity 创建时立即调用 `finish()` 和 `finishAndRemoveTask()`，瞬间阻断其运行和显示。
   - 支持动态配置拦截规则（支持包名或类名关键字匹配）。

2. **全局启动日志抓取 (System Server Hook)**：
   - 安全 Hook `system_server` (`ActivityTaskManagerService` / `ActivityManagerService`) 的 `startActivity` 等核心调度方法。
   - 异步且安全地记录全系统的 Activity 启动历史至 `/data/local/tmp/intercept_logs.txt`。
   - 提供独立且直观的 App UI 用于查看、刷新和清除这些日志。

3. **独立可视化配置 App**：
   - 提供可视化操作界面，无需修改代码即可动态配置拦截规则。
   - 规则支持换行分隔，可以使用 `#` 或 `//` 进行注释。
   - 支持通过独立 App 内的开关动态控制日志记录的启停。

## 使用说明

### 1. 安装与激活
1. 编译并生成 APK，或直接安装根目录下的 `InterceptActivity-debug-1.0.apk` 到你的 Android 设备。
2. 打开 LSPosed 管理器，在模块列表中找到并启用 **Activity Interceptor**。
3. **重要：**
   - 勾选 **系统框架 (Android 系统)** 以启用全局 Activity 日志抓取功能。
   - 勾选 **你想要拦截的目标应用** 以使 `onCreate` 拦截对其生效。
4. 重启设备（或软重启）。

### 2. 配置与使用
1. 打开桌面上名为 **Activity Interceptor** 的 App（请授予应用 Root 权限以便应用能正确设置配置文件权限）。
2. 在输入框中填写你需要拦截的规则（每行一条，部分匹配包名或 Activity 类名），点击保存。
   *示例（默认拦截 MIUI 安全中心的相关 Activity）：*
   ```text
   com.miui.securityscan.MainActivity
   com.miui.securityscan.MainEntryActivity
   ```
3. 在日志区域，你可以点击**刷新日志**来查看当前系统启动过的所有 Activity（非常有助于你在配置规则前抓取准确的类名）。
4. 顶部有日志开关，可以随时暂停或恢复系统级全局日志记录。

## 核心代码与工作原理

- **全局日志监控 (`hookSystemServerSafe`)**：位于 `MainHook.java`，通过 `afterHookedMethod` 安全监听 `system_server` 的调度方法，使用单线程池异步写入日志文件，经过多层 `try-catch` 保护，确保绝不导致系统重启或卡顿。
- **目标应用拦截 (`hookTargetApp`)**：动态读取 `XSharedPreferences` 中的规则列表。当目标应用创建 Activity 时，验证并匹配类名和包名，如果命中则立刻 `finish()` 和 `finishAndRemoveTask()`。
- **规则下发机制**：通过 `MainActivity.java` 提供配置界面。鉴于 LSPosed 兼容性问题，配置界面的保存过程会自动利用 Root 权限进行 `chmod 666` 修正权限，以确保 `system_server` 和目标应用都能顺利读取规则。

## 注意事项

- **Root 权限需求**：App 配置界面依赖 Root 权限（执行 `su` 命令）进行全局日志文件的初始化和配置文件权限修正。如果未授予 Root 权限，可能导致规则无法生效或日志无法正常显示。
- 请谨慎使用宽泛的规则，以免误拦截目标应用的必要界面，甚至导致死循环或应用无响应。
