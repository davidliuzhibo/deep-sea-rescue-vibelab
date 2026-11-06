# 深海救援站 / DeepSeaRescue

PICO Vibe Lab 私有投稿源码，来自用户指定的 DeepSeaRescue 当前工程。

- 应用包名：`com.vrgames.deeprescue`
- 版本：`1.0.0-full-rc1` / versionCode `2`
- 技术栈：Android Spatial；手势交互、混合现实舞台
- 主要 AI 编程工具 / 模型：Codex / GPT-6 Astra（初始主要开发；当前工程后续有 Claude 修改）

## 构建

仓库根目录就是 Gradle 根目录。准备 JDK 21 和 Android SDK 35，设置 JAVA_HOME 和 ANDROID_HOME，然后运行：

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
```

Windows 使用 `gradlew.bat`。Gradle 8.13 分发采用 SHA-256 固定校验的镜像；Gradle、SDK 和 Maven 依赖首次获取需要网络。

签名配置是可选输入。投稿仓库不包含原开发者私钥和本机路径配置；没有外部签名配置时生成 `app/build/outputs/apk/release/app-release-unsigned.apk`，供发布平台后续签名。运行时资源已包含，无需调用任何模型生成服务。

## 内容与验证边界

当前源码与提供的正式 APK 版本一致，投稿整理不修改游戏行为、关卡或素材。开局内容置于玩家前方，不需要主动扫描桌面或墙面，不把这一版本描述为真实表面识别。

原始玩法说明和历史验证边界见 [原项目说明](docs/原项目说明.md)，素材记录见 [素材来源](docs/素材来源.md)。其中程序化通关不等同于真人光学手势、难度或舒适度验收。本轮只准备投稿材料、核对文件并重建源码，不重新操作模拟器或真机。
