# 轻风 Breeze（Android）

吹风机扫码 → 选时长 → 支付 → 吹风 的极简客户端。界面按 `../ui/轻风-界面原型.html` 落地。

## 结构

```
app/
├── build.gradle.kts              根构建（AGP 8.6.1 + Kotlin 2.0.21 + Compose 插件）
├── settings.gradle.kts
└── app/
    ├── build.gradle.kts          compileSdk 35 / minSdk 24 / targetSdk 34
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/           strings、themes
        └── java/com/pangguai/breeze/
            ├── BreezeApp.kt      Application，持有 Api + Repo 单例
            ├── MainActivity.kt   唯一 Activity，Compose 宿主 + ZXing 扫码 + 手动输入弹窗
            ├── BreezeViewModel.kt  单屏状态机 HOME/SCAN/PAY/DONE + 倒计时
            ├── data/
            │   ├── Sign.kt       header 签名（SHA-256）——逆向自 HeadInterceptor
            │   ├── Api.kt        OkHttp form POST，自动带签名/token
            │   ├── BreezeRepo.kt scan/v2 → details → skus → prePay 的只读链路
            │   └── Models.kt     Device / Plan / PayChannel / StepState
            └── ui/
                ├── theme/BreezeTheme.kt   设计令牌：深色 #0B0F0E + 薄荷绿 #3FD69B
                ├── Common.kt             卡片 / 胶囊 / 按钮 / 顶栏
                ├── Icons.kt              Canvas 画的取景框、风线、对勾
                ├── HomeScreen.kt         大鱼眼「扫码吹风」
                ├── ScanScreen.kt         取景框 + 扫描线
                ├── PayScreen.kt          设备卡 + 时长档位 + 渠道 + 合计
                └── DoneScreen.kt         步骤条 → 倒计时
```

## 构建

工具链已装好（不污染系统），全部在 `C:\dev-toolchain`：

| 组件 | 路径 |
|---|---|
| JDK 21 | `C:\dev-toolchain\jdk21` |
| Gradle 8.9 | `C:\dev-toolchain\gradle-8.9` |
| Android SDK（platform-tools / android-35 / build-tools 35.0.0） | `C:\dev-toolchain\android-sdk` |
| 依赖缓存 | `C:\dev-toolchain\gradle-home` |

一键构建（推荐）：

```powershell
.\toolchain\build.ps1
# 产物：dist\breeze-debug.apk
```

手动构建要注意两点：

1. **路径不能有中文**——AGP 对非 ASCII 路径会报 `Your project path contains non-ASCII characters`。
   已建 ASCII 联结：`C:\breeze-src` → `F:\个人项目\pangguai`，从联结路径构建：
   ```powershell
   $env:JAVA_HOME="C:\dev-toolchain\jdk21"
   $env:ANDROID_HOME="C:\dev-toolchain\android-sdk"
   $env:GRADLE_USER_HOME="C:\dev-toolchain\gradle-home"
   & "C:\dev-toolchain\gradle-8.9\bin\gradle.bat" -p C:\breeze-src\app assembleDebug --no-daemon
   ```
2. `gradle wrapper` 会去 HEAD `services.gradle.org` 校验而失败（该域名只回 307），所以不用
   wrapper，直接用本地 `gradle.bat`。

装到手机（USB 打开调试）：

```powershell
& "C:\dev-toolchain\android-sdk\platform-tools\adb.exe" install -r dist\breeze-debug.apk
```

也可以直接用 Android Studio 打开 `app` 目录（Studio 自带 JBR 是 25，编译建议改用上面的 JDK 21）。

## 发版与自动更新

思路：不经过应用商店，APK 自己从你的服务器检查新版本、下载、调起系统安装器覆盖安装。

```powershell
# 1. 改版本号（versionCode 必须递增，否则手机端不会认为有新版本）
notepad app\version.properties      # versionCode=3  versionName=1.2.0

# 2. 打正式包（固定签名，可覆盖升级）
.\toolchain\build.ps1 -Release
#    产物：dist\breeze-1.2.0.apk + dist\latest.json

# 3. 传到 https 目录
.\toolchain\deploy.ps1 -Ssh root@你的服务器 -RemoteDir /var/www/breeze
#    或目录已挂载：.\toolchain\deploy.ps1 -LocalDir D:\www\breeze
```

三个必须记住的点：

1. **签名是命门**。`toolchain\keystore\breeze-release.jks`（alias `breeze`，密码见
   `app\keystore.properties`）丢了就再也没法覆盖升级，只能让所有人卸载重装。请备份。
2. **`updateBase` 必须是 https 且以 `/` 结尾**，指向放 `latest.json` 的目录；改完要重新
   打一次包（这个值编进了 `BuildConfig.UPDATE_BASE`）。App 里 `usesCleartextTraffic=false`，
   所以 http 地址拿不到更新。
3. **`latest.json` 的 `versionCode` 只要大于手机上装的那版**，App 下次启动就会弹更新框；
   首页底部那行 `v1.1.0（2）· 检查更新` 也能手动触发。

更新是怎么装的：`PackageInstaller` 开会话写入 APK → 系统弹「是否安装」→ 确认后覆盖。
不需要存储权限、不用 FileProvider；第一次会让你允许「安装未知应用」。

## 登录

没有 token 什么都做不了（连 `goods/scan/v2` 都算未登录），所以第一屏是登录页：

| 步骤 | 接口 | 参数 |
|---|---|---|
| 发短信 | `common/sms/sendCode` | `{phone, template:"reg"}` |
| 登录 | `user/reg` | `{channel:"android_app", phone, verify:"<短信码>"}` → `data.token` |

坑：`user/smsCode` 是「已登录后换手机号」用的，未登录调用一律返回「未登录」；
`user/reg` 的短信码字段名是 `verify`（R8 把常量 `AdFlowReporter.TRAFFIC_VERIFY` 的值改成了这个），
传 `authCode`/`code` 只会得到「参数不正确」。

**登录态是持久的**：token + 手机号存 DataStore（`data/Session.kt`），冷启动时
`BreezeRepo.restoreSession()` 读回来直接进首页，不用再收一次短信；只有点「退出登录」
才会清掉（`BreezeViewModel.logout()` → `BreezeRepo.logout()`）。

## 扫码

**不用** `zxing-android-embedded` 的 `CaptureActivity` —— 那是独立 Activity，自带一套横屏取景 UI，
和竖屏深色界面对不上（之前「强行横屏」就是它）。现在换成 `ui/QrScanner.kt` + `ui/ScanScreen.kt`：

- CameraX `Preview` + `ImageAnalysis`（`STRATEGY_KEEP_ONLY_LATEST`）绑在当前 Activity，整屏竖屏预览
- 解码在独立单线程 executor 里做，不占主线程，预览不掉帧
- 只取 YUV 的 Y 平面喂 `PlanarYUVLuminanceSource`；QR 检测本身与旋转无关，不需要转图
- 叠加层用 Compose 画：四周压暗 + 正方形取景框 + 四角 + 上下往返扫描线（微信那种）
- 相机权限运行时申请，拒绝时提示可以改用手动输入

依赖：`com.google.zxing:core:3.5.3` + CameraX 1.3.4（core / camera2 / lifecycle / view）。

## 支付与启动（写链路）

`BreezeViewModel.pay(activity)` 已经按真实顺序接好：

```
trade/create            → 建单，拿 tradeNo（额外 header：imei/categoryCode/lat/lng + orderRiskSign/orderRiskTimestamp）
pay/unify/prePay        → {orderNo, payType:<渠道 method>, againFlag:"1"} → data.prepayParam
支付宝 PayTask.payV2     → prepayParam 就是 orderInfo 串，resultStatus="9000" 即成功
微信   WXAPIFactory      → prepayParam 是 JSON（appid/partnerid/prepayid/noncestr/timestamp/sign/package）
                          组装 PayReq 丢给微信，结果由 WXEntryActivity 回传 errCode=0 表示成功
order/pay_status        → {orderNo, payType} 轮询确认（最多 6 次 × 1.2s）
trade/order/start       → {orderNo} 启动设备
machine/device_is_start → {orderNo} 轮询启动状态（最多 10 次 × 1s）
```

关键点：

- **`orderRiskSign`** = SHA-256(`appSecret=…&categoryCode=…&channel=android_app&imei=…&lat=…&lng=…&timestamp=…&token=…&version=…&/trade/create`)，它的 secret 与普通 header 签名是**同一串**。少这两个头服务端会拒单。
- **支付宝**用官方 `com.alipay.sdk:alipaysdk-android:15.8.42`（Maven Central 上有），
  和原 App 的 `AliSdkExtKt.alipay()` 完全一样；已在 Manifest 里补了 Android 11+ 的 `<queries>`，
  否则探测不到支付宝客户端只能走 H5。
- **微信**用官方 `com.tencent.mm.opensdk:wechat-sdk-android:6.8.40`。`WXEntryActivity` 必须在
  `<applicationId>.wxapi` 下且 `exported="true"`，debug 变体的 applicationId 带 `.debug`，
  所以类是 `com.pangguai.breeze.debug.wxapi.WXEntryActivity`（打 release 要另建一份）。
  微信对 appId 有签名校验：如果 `prepayParam` 里的 appid 是原 App 在开放平台注册的，
  而本 APK 签名不同，`registerApp` 会失败 / 微信返回 `errCode=-1`，界面会直接显示这个原因。
- **`payType` 取渠道的 `method`**（实测该设备返回 52 银联 / 13 支付宝 / 32 微信 / 91 农行）。
  只接了支付宝和微信；银联、农行需要各自的 SDK，标「暂未接入」且不可选。
- 任何一步失败都会停在「完成」页对应步骤、把原因写进副标题并 toast，**不会假装成功**。
  错误文案里带服务端 `code`（例如 `未登录（2）`），方便判断是签名、token 还是参数问题。

## 吹风计时（墙钟锚点）

倒计时**不是进程里的递减计数器**，而是只存一个「结束时刻」`endAt`（epoch ms），剩余时间每次由
`endAt - System.currentTimeMillis()` 算出来，并且落盘到 DataStore（`Session.run*`）。

- 计时起点 = **支付成功那一刻**（`beginRun()` 紧跟 `confirmPaid()`），不是「设备启动成功」，
  也不是「用户切回 App」。去支付宝付款、切微信、退到桌面这段时间设备一样在吹，都算进去。
- 冷启动 / 进程被杀后重开：`restoreRun()` 读回 `endAt`，还没吹完就直接进「吹风中」页接着倒计时。
- 到点：`endAt=0` + 清盘 + toast「吹风结束」；`stop()` 手动结束同样清盘。
- 界面每秒 `tick` 一次触发重组，`running` / `remain` / `progress` 都是 `endAt` 的派生值。

## 历史订单

首页底部「历史订单」→ `Screen.HISTORY` → `BreezeRepo.orders(page, size)`：

- 接口 `order/list`，body 只有 `{page, pageSize}`（`ApiService.java:796`，调用点 `MyViewModel.getOrderList`）。
- 列表键是 `data.items`（**不是** `list`/`records`），一次取 20 条。
- 卡片字段：`machineName`（设备）、`shopName`、`createTime`、`markMinutes`、`payPrice`（缺省回落 `markPrice`）。
- 状态文案与官方一致：`0` 未支付 / `1` 支付超时 / `2` 已支付 / `3` 已完成 / `4` 退款中 / `5` 已退款。

## 数据从哪来

扫码后的设备名、时长档位、支付渠道**全部来自服务端**，代码里没有写死价格：

| 界面数据 | 接口 | 取值 |
|---|---|---|
| `goodsId` | `goods/scan/v2` | 二维码 URL 里的 `NQT`/`IMEI`/`SN` → 返回 `data.id` |
| 设备名/区域/在线状态 | `goods/normal/details` | `name` / `orgName` / `deviceErrorCode` |
| 时长 + 价格 | `goods/normal/skus` | `skuId`、`extAttr.perPrice[].time/price`（按分钟去重）、`discountPrice`、`defaultModel` |
| 支付方式 | `payChannelRoute/prePay` | `method`、`methodName`、`canChecked`、`defaultFlag`、`thirdChannel` |
| 合计金额 | `trade/preview/v4` | `realPrice`（换档位就重问一次，显示的就是真正要付的钱） |

服务端很多字段是 JSON null，`JSONObject.optString` 会给出字面量 `"null"`，
所以统一走 `BreezeRepo.clean()` 洗一遍（渠道副标题之前显示 `null` 就是这个原因）。

未登录 / 网络失败时会回落到 `BreezeRepo.demoPlans()` / `demoChannels()` 的示例数据，
所以现在打开就能看到完整界面；接上真实 token 后自动用服务端数据。

## 验证状态

| 部分 | 状态 |
|---|---|
| 发短信 / 登录 | ✅ 已用真机号 + 真短信码跑通（Python 侧同一套签名） |
| 登录态持久化 | ✅ DataStore，杀进程 / 重启手机后自动进首页 |
| 扫码 → 详情 → 档位 → 预结算 → 收银台 | ✅ 已跑通，返回真实设备/价格/4 个渠道 |
| 相机扫码（竖屏 CameraX + ZXing） | ⚠️ 编译通过、权限/绑定逻辑已写，**未上真机验证** |
| 建单 → 支付宝 → 启动 | ⚠️ 代码已按逆向出的参数接好，**尚未实盘跑过**（会真花钱、真启动设备） |
| 微信支付 | ⚠️ SDK 与回调已接好，但 appid 是原 App 在开放平台注册的，**签名不匹配会被微信拒**（界面会显示 errCode） |
| 吹风倒计时（墙钟 + 落盘恢复） | ⚠️ 逻辑已改完并编译通过，**未上真机验证**（退出 App / 杀进程后回来数字应对得上） |
| 历史订单（order/list） | ⚠️ 接口与字段已实测，**界面未上真机验证** |
| 自动更新（检查 → 下载 → 覆盖安装） | ⚠️ 已编译、`latest.json` 已生成、`deploy.ps1` 自测通过，**未在真机上走完整更新流程**（取决于 `updateBase` 是否配好） |

实盘验证建议：先在 `../tools/qiekj_client.py` 里跑一遍只读链路确认 token 有效，
再用本 APK 扫一台空闲机器、选最便宜那档，确认「完成」页四步都变绿。
