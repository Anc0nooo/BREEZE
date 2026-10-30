# 轻风（工作名）— 吹风机极简客户端 · 整体设计

> 目标：绕开官方 App 的 6 处广告与冗余跳转，只保留「登录 → 扫码 → 支付 → 吹风」一条直线。
> 登录方式：手机号 + 短信验证码；支付方式：余额（钱包，payType=2）。
> 工作名「轻风」可随时改，包名暂定 `com.pangguai.breeze`。

> ⚠️ **这是一份历史设计稿（2026-10-29 起）**，保留原样以便对照思路演进。
> 其中若干结论后来被实测推翻，以根目录 [README](../README.md) 的「技术要点」为准，主要差异：
>
> - 支付不是「小票余额代扣」，最终接了 **支付宝 SDK + 微信 SDK**（小票那档在真机收银台根本不可选）。
> - 扫码页**没有**用 ZXing 库自带的 `CaptureActivity`（它会强制横屏），改成了 CameraX + 自绘叠加层。
> - 发短信接口是 `common/sms/sendCode`、登录码字段名是 `verify`，不是本稿里写的 `user/smsCode`。
> - 头部签名只签 header；body 的 `_sign`/`_appid` 是死代码，从未启用。

---

## 1. 产品定义

| 项 | 值 |
|---|---|
| 目标用户 | 自己（一人用，不追求兼容性广度） |
| 核心路径 | 打开 App → 扫码 → 选档 → 一键支付 → 吹风开始 |
| 路径长度 | 4 次点击（登录态保存后：扫码 1 击 + 支付 1 击） |
| 砍掉的东西 | 开屏广告、扫码闸门广告、收银台广告、红包横幅、看视频任务、自更新、IM、商城、BLE |
| 设备范围 | 仅吹风机（categoryCode "03"）。遇到其他设备给出提示「暂不支持」 |

## 1.1 支付方式：不能用「余额支付」当默认方案（2026-10-29 修正）

反编译证据：`PayTypeEnum.WALLET(2, "钱包")` 确实存在，也有 `pay/unify/pay` + `PayModeVm.walletPay`，**但它不是通用现金余额**：

- `PayModeFragment` 里 WALLET 渠道的展示文案是「**本单可用小票，小票余额 N 元**」，取的是 `UserBalance.tokenCoin`，而非 `UserBalance.balance`。
- 两道开关：`UserBalance.shopTokenCoinSet`（学校/商家是否开启小票）+ 余额 ≥ 订单金额，否则 `setCanChecked(false)` 显示「小票余额不足」或直接不出现。

→ 所以收银台上看不到该选项是常态。**支付渠道必须由服务端 `payChannelRoute/prePay` 的实际返回决定**，抓包/直连实测拿到真实渠道列表后再定方案。

**对自建客户端更友好的候选**（支付在服务端代扣完成，无需拉起第三方 SDK，纯 HTTP 即可）：
`ALIPAY_NOT_PASSWORD(15, 支付宝免密)`、`CUP_UNIONPAY(53, 云闪付无感)`、`CUP_BOCOM/ICBC`、`ZY_PAY(33)`、`ONE_CARD`。
**需要拉起 SDK 的**：`ALIPAY(13)`、`WECHAT(32)`——自建客户端成本高，作为备选。

实测第一步目标：用真机和真经历过一次完整支付，记录收银台返回的 `PayChannelRoute[]`（method / balance / 可用状态），据此定支付方案。

```
[登录] --验证码登录--> [首页]
[首页] --点大按钮--> [扫码]（ZXing 相机页）
[扫码] --解析出 NQT/IMEI/SN--> [选档]（设备详情+档位列表，默认选第一档）
[选档] --点「立即支付」--> [支付·启动·吹风]（一屏串完：建单→余额支付→启动→倒计时）
```

- 全程不弹任何广告；`ScanAdxGate`、`cashierAdxHit` 等客户端闸门在新 App 中**根本不存在**。
- 启动轮询（`machine/device_is_start`）成功后进入倒计时；倒计时结束只提示「本次吹风已完成」，无主动结束接口（服务端按时长自动停）。

## 3. 界面与图标（详见对话中的界面稿）

**视觉基调**：深色 + 青绿（teal）主色，扁平、大按钮、少文字。风 = 动感、轻。

**每屏要点**：

| 屏 | 内容 | 关键控件 |
|---|---|---|
| 1 登录 | Logo、手机号、验证码 + 获取按钮、登录按钮、协议文案 | 输入框 ×2 |
| 2 首页 | 顶部手机号 + 余额，中央超大扫码按钮，次级「手动输入设备号」，最近一笔订单卡片 | 大圆按钮 |
| 3 选档 | 设备名 + 在线状态，档位单选卡片（时长/价格），底部合计 + 立即支付 | 单选卡片 ×3 |
| 4 支付·启动 | 步骤条：订单创建 ✓ → 支付成功 ✓ → 正在启动… → 完成；转圈动画 | 步骤指示 |
| 5 吹风中 | 大倒计时 08:47，风纹动画，进度条，订单信息卡（订单号/时长/金额），完成按钮 | 倒计时 + 动画 |

**图标方案（iconfont.cn 阿里矢量图库）**：建一个 iconfont 项目，按下列关键词检索官方免费图标库，统一选「线性 1.5px 描边」风格，下载 SVG 后用 Android Studio 的 Vector Asset 直接转 drawable：

| 用途 | iconfont 检索词 | 备注 |
|---|---|---|
| Logo / 吹风 | `风` / `wind` / `吹风机` | 三条飘带风纹即可当 Logo |
| 扫码 | `扫一扫` / `scan` / `二维码扫描` | 四角框 + 中线 |
| 钱包/余额 | `钱包` / `wallet` / `余额` | 首页顶部 |
| 手动输入 | `键盘` / `输入` / `edit` | 次级按钮 |
| 倒计时 | `计时器` / `闹钟` / `timer` | 运行页 |
| 成功/完成 | `对 勾` / `success` / `check` | 步骤条 |
| 加载/启动 | `加载` / `loading` / `风车 windmill` | 启动页（风车最贴题） |
| 返回/关闭 | `返回` / `关闭` | 通用 |

> 备选：iconfont 官方「通用图标库」里的 wind / saoma / qianbao 命名图标；如果不想建项目，直接用 Material Symbols 的 `air`、`qr_code_scanner`、`account_balance_wallet` 也完全够，风格同样干净。

## 4. 技术架构

**原生 Android（Kotlin），单 Activity + Compose，依赖压到最少**：

```
轻风 App
├─ ui/            Compose 界面（5 屏，见上）
├─ scan/          ZXing-android-embedded（journeyapps，无广告、无推送 SDK）
├─ net/
│   ├─ Api.kt           极简 HTTP 客户端（OkHttp，手写表单 POST，不引 Retrofit）
│   ├─ HeadSign.kt      复刻 HeadInterceptor：header SHA-256 签名
│   ├─ BodySign.kt      复刻 SignInterceptor：body SHA-1 签名（_appid 盐）
│   └─ Session.kt       token 存取（DataStore，登录态长期有效）
├─ flow/
│   ├─ LoginFlow.kt     smsCode → reg → token
│   ├─ ScanFlow.kt      goods/scan/v2 → details → skus
│   └─ PayFlow.kt       preview → create → unify/pay → pay_status → start → device_is_start
└─ MainActivity.kt     唯一 Activity，扫码页由 ZXing 库自带
```

**关键实现点**：

1. **双签名复刻**（所有请求自动处理，业务代码无感知）：
   - header：`appSecret=<REDACTED>&channel=android_app&timestamp=<ts>&token=<token>&version=1.144.0&/<path>` → SHA-256 → hex → `Sign` 头；另有 `Timestamp` 头（毫秒）。
     笔记里两个 header 名必须首字母大写，小写会命中服务端「版本过低」的兜底分支。
   - body：**线上不签**。`SignInterceptor` 虽然在源码里，但 `NetworkApi` 从未注册它，属死代码。
   - `trade/create` 额外的位置头（imei/categoryCode/lat/lng）在 PayFlow 里单独加，lat/lng 可写死一个常用坐标。
2. **version 头固定 1.144.0**，跟随官方版本号策略待定（若服务端强校验新版本再改）。
3. **余额展示**：不额外调接口，复用选档后 `payChannelRoute/prePay` 返回的钱包渠道信息（含余额），在支付确认时展示「余额支付 ¥x.xx」。
4. **轮询节流**：`order/pay_status` 每 1s、`machine/device_is_start` 每 2s，带超时上限（30s 失败给手动重试按钮）。

## 5. 屏 × 接口映射

| 屏 | 顺序 | 接口 | 备注 |
|---|---|---|---|
| 1 登录 | 1 | `POST user/smsCode` | 参数名待实测（大概率 `{mobile/phone}`） |
| | 2 | `POST user/reg` | 返回 token，存 DataStore |
| 2 首页 | — | 无请求（余额/订单信息用缓存） | 冷启动静默校验 token 有效性 |
| 3 选档 | 1 | `POST goods/scan/v2` | `{NQT|IMEI|SN: <码值>}` → goodsId |
| | 2 | `POST goods/normal/details` | `{goodsId}` → categoryCode（校验 =="03"，否则提示不支持） |
| | 3 | `POST goods/normal/skus` | `{goodsId}` → 档位列表 |
| | 4 | `POST trade/preview/v4` | 选档试算，展示合计 |
| 4 支付·启动 | 5 | `POST trade/create` | 位置头 + items JSON → orderNo |
| | 6 | `POST payChannelRoute/prePay` | 拿钱包余额展示 |
| | 7 | `POST pay/unify/pay` | `{orderNo, payType:"2"}` 余额支付 |
| | 8 | `POST order/pay_status` | 轮询（1s） |
| | 9 | `POST trade/order/start` | `{orderNo}` 下发启动 |
| | 10 | `POST machine/device_is_start` | 轮询（2s）→ true 进倒计时 |
| 5 吹风中 | — | 无 | 本地倒计时；可加 `order/pay_status` 之外的状态查询（可选） |

## 6. 工程参数

| 项 | 值 |
|---|---|
| 包名 | `com.pangguai.breeze` |
| minSdk / targetSdk | 24 / 35 |
| 语言/UI | Kotlin 2.x + Jetpack Compose (Material3, 深色主题固定) |
| 网络 | OkHttp 4.x（手写表单，不引 Retrofit/Gson，JSON 用 org.json 即可） |
| 扫码 | zxing-android-embedded 4.x |
| 持久化 | DataStore（token、最近订单） |
| 依赖总数 | ≤ 8 个，无任何广告/统计/推送 SDK |
| 签名 | 自建 keystore，apksigner v2 签名（官方包不冲突，两者可共存） |

## 7. 风险与开工前待确认项（2026-10-29 静态已全部钉死）

1. ~~登录参数名~~ **已钉死**：`user/smsCode {phone}`、`user/reg {channel:"android_app", phone, code}`（LoginViewModel 反编译实锤）。
2. ~~payType=2 确认~~ **已钉死**：PayTypeEnum.WALLET(2)。
3. ~~items JSON 字段~~ **已钉死**：`(goodsId, skuId, time, num:"1", timeValue)`。
4. **验证码图形校验**：ApiService 里有 `isNeedCaptcha / checkCaptcha / integralCaptcha`，属积分任务模块，登录流程未见调用；以实发一次验证码为准。
5. **token 有效期**：静态不可见，实测；过期则提示重新登录（无静默重登）。
6. **风控**：自建 UA 固定、请求频率正常使用级别，勿写自动化批量脚本。
7. **官方 App 升级换签名算法**：`version` 头跟着官方走即可，算法变动概率低。
8. **TLS**：release 只信系统 CA（networkSecurityConfig 实锤），自建客户端直连不受影响；但**第三方抓包装用户证书抓不到官方 App 的包**，验证改用「直连实测」方案：用 Python 复刻双签名直接调接口，从真实验证码登录开始逐个验证（比抓包更快更准）。

## 8. 里程碑

1. **M1 抓包核对**（真机 + mitmproxy，半小时）：过一遍官方流程，确认第 7 节 6 个待定项。
2. **M2 骨架**：工程搭建 + 双签名网络层 + 登录跑通（token 入库）。
3. **M3 主链路**：扫码 → 选档 → 余额支付 → 启动 → 倒计时全流程真机验证。
4. **M4 打磨**：图标（iconfont）、动画、错误兜底、余额不足提示（引导去官方 App 充值）。
