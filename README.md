# 弈眼 (YiEye)

一个基于屏幕截屏识别的中国象棋（Xiangqi）实时分析助手。通过截取屏幕上棋类 App/小程序的棋盘画面，用 YOLOv5 模型自动识别棋盘和棋子，再交给 Pikafish 引擎计算，最终以悬浮窗的形式实时显示推荐走法。

## 主要功能

- **屏幕棋局自动识别**：通过 Android `MediaProjection` 截取屏幕，自动定位棋盘并识别棋盘上的棋子布局，生成局面 FEN 字符串，无需手动摆谱。
- **走法引擎分析**：接入 [Pikafish](https://github.com/official-pikafish/Pikafish) 引擎（NNUE 神经网络评估），计算当前局面的最佳走法与后续应对（ponder move）。
- **悬浮窗实时提示**：分析结果通过可拖动的悬浮窗展示，方便一边看棋一边看提示，不需要切换回本 App。
- **自动识别红黑方与走子方**：首帧自动判断我方执红或执黑，并在每次局面变化后自动判断轮到哪一方。
- **局面变化检测**：使用感知哈希（pHash）比对前后两帧截图，只有棋盘发生实际变化时才重新触发分析，减少无意义的计算和耗电。
- **限速截屏，兼顾速度与发热**：截屏频率固定为每 3 秒一次，而不是连续高频截屏，在保证走法提示不明显滞后的同时，避免截屏+识别长时间高负载运行导致手机发热、耗电过快。
- **可调计算深度**：在主界面可选择引擎搜索深度（6~30），在计算强度和速度之间自由权衡。
- **后台前台服务运行**：分析过程以前台服务（Foreground Service）方式运行，并处理好电池优化白名单提示，保证截屏识别不被系统杀后台。

## 技术架构

| 模块 | 说明 |
|---|---|
| 屏幕截图 | `MediaProjection` + `ImageReader`（[AnalysisService.java](app/src/main/java/com/yieye/xiangqi/AnalysisService.java)） |
| 棋盘/棋子识别 | ONNX Runtime + YOLOv5 模型 `middle.onnx`（[YoloV5Detector.java](app/src/main/java/com/yieye/xiangqi/YoloV5Detector.java)、[ChessBoardParser.java](app/src/main/java/com/yieye/xiangqi/ChessBoardParser.java)） |
| 局面转 FEN / 中文记谱 | [Utils.java](app/src/main/java/com/yieye/xiangqi/Utils.java) |
| 引擎分析 | Pikafish（C++，JNI 桥接，[pikafish_jni.cpp](app/src/main/cpp/pikafish_jni.cpp)、[EngineHelper.java](app/src/main/java/com/yieye/xiangqi/EngineHelper.java)） |
| 悬浮窗展示 | [FloatWindowManager.java](app/src/main/java/com/yieye/xiangqi/FloatWindowManager.java) |

第三方组件及其许可证详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。本项目整体以 **GNU GPLv3** 协议开源，见 [LICENSE](LICENSE)。

## 使用方法

### 环境要求

- Android Studio（建议最新稳定版）
- Android 7.0（API 24）及以上系统，仅支持 **arm64-v8a** 架构真机（模拟器通常为 x86，无法运行）
- NDK / CMake（用于编译 Pikafish 原生库，Android Studio 会按 `app/src/main/cpp/CMakeLists.txt` 自动下载配置）

### 构建与安装

1. 克隆本仓库并用 Android Studio 打开。
2. 首次同步 Gradle 时会自动下载依赖并编译 Pikafish 原生库，耐心等待即可。
3. 如果你自己有签名密钥，可在项目根目录新建 `keystore/keystore.properties`（该文件已在 `.gitignore` 中，不会被提交），内容格式如下：

   ```properties
   storeFile=../keystore/你的密钥文件.jks
   storePassword=你的密钥库密码
   keyAlias=你的别名
   keyPassword=你的密钥密码
   ```

   不配置该文件也可以正常编译 Debug 版本，只是不会使用自定义签名。
4. 用真机（arm64）运行或打包 APK 安装。

### 使用步骤

1. 打开 App，按提示点击"开启悬浮窗权限"，跳转到系统设置授权。
2. 点击"启动分析服务"，系统会弹出屏幕录制/截屏授权，点击允许。
3. 授权后 App 会自动切到后台，此时打开你要对弈的象棋类 App/小程序即可。
4. 悬浮窗会实时显示识别到的局面走到第几步、当前轮到哪一方、以及引擎推荐的走法和续着。
5. 如果发现分析卡住或长时间无响应，可在主界面调整"计算深度"（数值越小分析越快，越大越强但耗时更久）。
6. 不需要时点击"停止分析服务"关闭悬浮窗和后台服务。

### 常见问题

- **提示需要关闭电池优化**：部分厂商系统会限制后台服务，请在弹出提示时点击"电池优化设置"，将本 App 加入不受限制/白名单，避免识别中途被系统杀掉。
- **识别不到棋盘**：确认棋盘完整显示在屏幕上，且未被其他悬浮窗/键盘遮挡；不同棋类 App 的棋盘样式差异较大，识别模型可能存在识别不准的情况。

## 免责声明

本项目仅供学习交流与个人技术研究使用，请遵守你所在地区的相关法律法规以及你所使用的第三方象棋 App/平台的用户协议，不得用于任何形式的商业作弊、破坏公平竞技环境等用途，因使用本项目产生的一切后果由使用者自行承担。

## 开源协议

本项目基于 [GNU GPLv3](LICENSE) 协议开源，这是因为项目集成了 GPLv3 协议的 Pikafish 引擎源码。这意味着：

- 你可以自由使用、修改、分发本项目；
- 但任何基于本项目的衍生作品，在分发时也必须以 GPLv3（或兼容协议）开源，并保留原作者的版权声明；
- 详见 [LICENSE](LICENSE) 及 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
