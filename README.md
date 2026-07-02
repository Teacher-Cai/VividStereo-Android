# VividStereo-Android

一款基于局域网的多设备音乐同步播放 Android 应用，让多台 Android 设备在同一 WiFi 下同时、精准地播放同一首歌曲，打造"立体声"或"环绕音"般的听音体验。

![image](app/src/main/res/mipmap-xxxhdpi/ic_launcher_adaptive_fore.png)

## 项目简介

【自制 APP】多设备音乐同步播放软件，音乐爱好者不容错过的音乐播放器！

- 视频介绍：https://www.bilibili.com/video/BV1yoWNekEHN/
- YouTube：https://youtu.be/CTvh30tzPr0?si=-WsIIJp-1CjTZXOF

## 核心功能

- **本地音乐播放**：基于 Android `MediaPlayer` 实现低延迟音频播放，支持从 `MediaStore` 读取设备中的全部音乐，或通过「Select Dir」手动指定音乐目录。
- **基础播放控制**：播放 / 暂停 / 停止 / 上一曲 / 下一曲。
- **多设备同步播放**：在同一局域网下，将一台设备设为主设备（Master），其它设备作为从设备跟随播放，所有设备在同一时刻开始播放同一首歌曲。
- **时钟偏差自动校准**：主设备通过 TCP 与每台从设备进行 100 次往返通信，计算网络传输与时钟偏差，得到每台设备的 `ip → delay` 映射表，再下发给所有从设备。
- **时间戳调度播放**：主设备根据调度时间戳 `preSetTs`，为每台从设备减去各自的延迟，使其真正"同时开嗓"。
- **系统延迟补偿**：可在界面中输入 `systemPlayDelay`（毫秒），用于补偿不同设备硬件的播放启动延迟。
- **配置持久化**：自动保存系统延迟与音乐目录到 `config.txt`，下次启动自动加载。
- **自动连播**：作为主设备时，播放完一首后自动切换到下一首，并通知所有从设备同步切换。

## 工作原理

```
┌──────────────┐   UDP 广播 (get_ip_address)    ┌──────────────┐
│  Master 设备  │ ────────────────────────────▶ │  从设备 A     │
│              │ ◀──────────────────────────── │              │
│              │   UDP (return_ip_address)      │              │
│              │                                └──────────────┘
│              │   UDP (tcp_sync)               ┌──────────────┐
│              │ ────────────────────────────▶ │  从设备 B     │
│              │                                └──────────────┘
│              │   TCP 端口 4000 测量 100 次 RTT
│              │ ────────────────────────────▶  得到各设备 delay
│              │
│              │   UDP 广播 (all_device_delay)  下发 delay 表
│              │ ────────────────────────────▶  所有从设备
│              │
│              │   播放时按 timeMs - deviceDelay 调度
└──────────────┘
```

1. **设备发现**：主设备通过 UDP 广播（`255.255.255.255`，端口 `4002`）发送 `get_ip_address`，所有收到消息的从设备把自己的 IP 回传给主设备。
2. **延迟测量**：主设备对每台从设备发起 TCP 连接（端口 `4000`），发送 `return` 指令进行 100 次时间戳往返，过滤传输时间 ≥ 10ms 的样本后取平均值，得到每台从设备的时钟偏差 `delay`。
3. **同步下发**：主设备把 `ip → delay` 映射表封装为 JSON 通过 UDP 发送给所有从设备，并标记进入 `synchronized` 状态。
4. **调度播放**：主设备计算一个未来的 `preSetTs`（通常为 `now + 1000ms`），为每台从设备算出 `specificDeviceTsMs = preSetTs - deviceDelay`，通过 UDP 发送 `music_play` 指令。从设备在到达时间戳后 `mediaPlayer.seekTo(0)` 并恢复音量，实现同步开声。
5. **暂停控制**：主设备发送 `timeMs = 0` 的 `music_play` 指令，从设备调用 `mediaPlayer.pause()` 暂停。

## 运行环境

- Android 7.0（API 24）及以上
- compileSdk / targetSdk：33
- 所有设备必须连接到同一个局域网（同一网段、互不隔离）

## 所需权限

应用首次启动时会申请以下权限：

| 权限 | 用途 |
| --- | --- |
| `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` | 读取本地音乐文件 |
| `WRITE_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` | 访问用户选择的音乐目录 |
| `INTERNET` | UDP / TCP 网络通信 |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | 获取与切换网络状态 |
| `WAKE_LOCK` | 同步播放期间保持唤醒，避免 CPU 休眠造成调度漂移 |
| `ACCESS_MEDIA_LOCATION` | 读取媒体文件位置信息 |

## 使用说明

1. 在两台（或多台）Android 设备上分别安装并打开 **VividStereo**。
2. 第一次启动时授予相关权限。
3. 列表会显示设备中的音乐（可点击 **Select Dir** 切换到指定目录）。
4. 在其中一台设备上点击 **Sync**：
   - 该设备成为 Master，广播 IP 收集。
   - 等待约 1~2 秒后下方状态变为 `sync state:synchronized~`。
5. 在 Master 设备上点击列表中的歌曲或 **Play / Next / Prev**，所有从设备会同步播放。
6. 通过 `system delay` 输入框可针对本机调整一个毫秒级的额外延迟，用于抵消不同设备的解码/输出启动耗时。
7. 配置在退出时自动保存，下次启动自动恢复。

## 技术栈

- **语言**：Java
- **构建**：Gradle 8.0.1（Android Gradle Plugin 8.0.1）
- **UI**：AndroidX AppCompat 1.4.1 + Material 1.5.0 + ConstraintLayout 2.1.3
- **音频**：`android.media.MediaPlayer`（`CONTENT_TYPE_MUSIC` + `FLAG_LOW_LATENCY` + `USAGE_GAME`）
- **网络通信**：`java.net.DatagramSocket`（UDP，端口 `4002`）、`java.net.Socket` / `ServerSocket`（TCP，端口 `4000`）
- **JSON**：`org.json.JSONObject` + Google Gson 2.8.2
- **架构**：单 Activity + 后台 UDP 接收线程 + 按需 TCP 测延迟线程池

## 项目结构

```
VividStereo/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/vividstereo/
│   │   │   ├── MainActivity.java   # 主界面、播放控制、同步流程编排
│   │   │   ├── GlobalInfo.java     # 单例：本地 IP、设备列表、delay 表等
│   │   │   ├── TcpCommu.java       # TCP 时钟偏差测量（100 次 RTT）
│   │   │   ├── UdpCommu.java       # UDP 广播/监听、获取本机 IP
│   │   │   └── ReadWriteUtils.java # 配置文件读写
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       ├── values/strings.xml
│   │       └── ...
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── gradle.properties
├── LICENSE                          # GPL v3
└── README.md
```

## 构建方式

```bash
# 克隆仓库
git clone https://github.com/<your-account>/VividStereo-Android.git
cd VividStereo-Android

# 使用 Gradle Wrapper 构建 Debug 包
./gradlew :app:assembleDebug

# 安装到已连接的设备
./gradlew :app:installDebug
```

> 仓库内已自带 `gradlew` / `gradlew.bat`，无需本地预装 Gradle。

## 已知限制

- 仅在同一局域网（广播可达）下工作，跨网段需要路由器支持 UDP 广播或自行扩展。
- 同步精度受设备硬件解码延迟、WiFi 抖动、CPU 调度影响，建议在 `system delay` 中按需微调。
- 音乐文件需在每台设备本地都存在（应用不负责跨设备分发音频文件）。

## 路线图

- [ ] 自动分发音乐文件到从设备
- [ ] 立体声分组（左/右声道拆分到不同设备）
- [ ] Web / 桌面端控制台
- [ ] 基于 NTP / PTP 的更高精度时钟同步

## 许可证

本项目基于 **GNU General Public License v3.0** 发布，详见 [LICENSE](LICENSE)。
