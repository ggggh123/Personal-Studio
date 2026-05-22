# P5 BIT 导入 — 真机 DoD 调试历程

> 开发日志素材。记录把"代码全绿、单测全过"的 BIT 教务导入功能在真机上从**完全跑不通**调到**端到端成功**的全过程。
> 单测覆盖的是我们*假设*的协议；真机暴露的是 BIT 服务器*实际*的协议。两者之间隔着 11 个坑。

## 背景

P5 实现阶段（subagent 驱动）产出的代码：单元测试 100% 通过、`assembleDebug` 绿、Hilt 图完整。所有协议细节（CAS 登录、jwapp 课表抓取）都是**基于 BIT101-Android 的旧实现 + 我们对 CAS 标准的假设**写的——没有一次真实请求验证过。

真机 DoD 第一次点"登录"就开始连环爆炸。下面是按时间顺序踩过的坑。

## 调试方法

关键工具：`adb logcat` + OkHttp `HttpLoggingInterceptor.Level.BODY`（临时）。每轮：
1. 临时把日志级别拉到 BODY（能看到请求/响应的完整 body）
2. 用户在真机操作一次，我 `adb logcat -d` 抓全量日志到文件
3. 解析 BIT 实际返回了什么 → 定位与我们假设的差异 → 修
4. 装机重试

外加 `WebFetch` 直接拉 `login.bit.edu.cn/cas/login` 看真实页面结构（发现了 SSO 迁移）。

事后已把 BODY 还原回 BASIC（BODY 会打印加密密码 + Set-Cookie，安全隐患）。

## 11 个连环 bug（按发现顺序）

### 1. CAS init HTML 结构假设错误
- **症状**：`CAS init: 'execution' field not found in HTML`
- **真相**：BIT 的 CAS 登录页不用传统 Spring 表单 `<input name="execution">`，而是把值放在 element id 里：`<p id="login-page-flowkey">` / `<p id="login-croypto">`
- **修复**：`extractById()` 用正则按 id 取值，兼容 text-node 和 attribute 两种形态
- **来源**：参考 BIT101 的 `GetSchoolInitLoginConvertFactory`（Jsoup `select("#login-croypto")`）

### 2. salt 是 base64 编码
- **症状**：`salt must be 16 ASCII bytes; got 24-char salt 'zWC9gZm+MKdxkZP6Sitlvg=='`
- **真相**：CAS 返回的 salt（croypto）是 **base64 编码的 16 字节**（22 字符 + `==`），不是原始 ASCII
- **修复**：用作 AES key 前先 `Base64.decode`

### 3. 登录成功判定误报
- **症状**：明明密码错却报"需要验证码"
- **真相**：旧逻辑"body 含'验证码'就 = CaptchaRequired"太宽——BIT 成功页的导航菜单里就有"验证码管理"等字样
- **修复**：改用 BIT101 验证过的启发式——**body 不含"用户名密码"就 = 成功**（"用户名密码"只在登录表单重渲染时出现）

### 4. SSO 域名迁移 + POST 跟随 302 丢凭据
- **症状**：登录返回 401，body 是登录页全文
- **真相**：`login.bit.edu.cn` 现在 **302 重定向到 `sso.bit.edu.cn`**。OkHttp 跟 GET 的 302 没问题，但**跟 POST 的 302 会按 HTTP 规范降级成 GET**，请求体（凭据）静默丢失
- **修复**：`LOCAL_BASE` 直接指向 `sso.bit.edu.cn`，不走 redirect
- **工具**：`WebFetch login.bit.edu.cn/cas/login` 直接看到 302 → sso

### 5. AES 模式错误（最关键的一个）
- **症状**：无论密码对错都报"用户名或密码错误"
- **真相**：我用了 **AES-CBC + 随机 IV + 64 字符随机前缀**（某些 Wisedu 变体）。BIT 实际用的是 **AES-ECB**（确定性、无 IV、无前缀）。服务器用 ECB 解我们的 CBC 密文 → 乱码 → 判定密码错
- **修复**：重写为 `AES/ECB/PKCS5Padding`，类从 `AesCbcCrypto` 重命名为 `CasAesCrypto`
- **验证**：用独立的纯 Python AES-128-ECB 实现交叉算出 golden value，确保符合标准而非自洽
- **来源**：BIT101 `AESUtils` 用的就是 `AES/ECB/PKCS5Padding`（仅观察算法，AGPL 不抄码）

### 6. CAS 是 service-bound 的
- **症状**：POST 到裸 `/cas/login` 返回 401
- **真相**：BIT 的 CAS 必须 POST 到**重定向链最终落地的那个带 `service=<callback>?sessionToken=<nonce>` 参数的 URL**，裸 `/cas/login` 不知道把 ticket 签发给谁
- **修复**：先 GET 受保护资源（jwapp index）触发 302 链 → 用 `Response.raw().request.url` 抓最终 URL → Retrofit `@Url` POST 到那个 URL
- **副产物**：登录流程整个翻转——先 GET 触发重定向，再 POST

### 7. Retrofit 错误响应体在 errorBody() 不在 body()
- **症状**：错密码被当成功，继续跑 jwapp 后报 "Unexpected JSON token at offset 0"（HTML 喂给 JSON 解析器）
- **真相**：401 是非 2xx，**Retrofit 的 `Response.body()` 返回 null**，错误响应体在 `errorBody()`。`body()?.string().orEmpty()` 得到 `""`，classify `"用户名密码" !in ""` → true → 误判成功
- **修复**：`(body() ?: errorBody())?.string()`

### 8. CAS 与 jwapp 是两个不同的 host
- **症状**：jwapp 请求全部 500 Internal Server Error（nginx）
- **真相**：CAS 在 `sso.bit.edu.cn`，教务 jwapp 在 `jxzxehallapp.bit.edu.cn`——我假设了同一个 host
- **修复**：`BitApiClient` 持有**两个 Retrofit 实例**（共享 OkHttpClient + cookie jar），分别针对 CAS host 和 jwapp host。校外 WebVPN 模式两个 host 各有对应的 webvpn 编码前缀
- **来源**：BIT101 `Options.kt` 的 `PROD_URLS`（jxzxehallappUrl / schoolLoginUrl 是分开的）

### 9. cookie jar 没把 session cookie 带到 POST
- **症状**：POST 请求无 Cookie header，CAS 认不出会话
- **真相**：自写的 `BitCookieJar` 两个 bug——(a) dedup 用 `(existing + new).distinctBy{name}` 保留了旧 cookie；(b) loadForRequest 只做 domain 前缀匹配，没用 OkHttp 标准的 `Cookie.matches()`（漏了 path/secure/hostOnly 语义）
- **修复**：键改成 `(domain, name)` 让新值覆盖旧值；加载用 `cookie.matches(url)`
- **坑中坑**：OkHttp 的 application interceptor 看不到 Cookie header（它由更底层的 BridgeInterceptor 添加），所以 logcat 里始终看不到 Cookie，一度误导排查方向

### 10. jwapp 模块端点需要 app 预热
- **症状**：登录成功、index 返回 200，但 `dqxnxq.do` 返回 403 Forbidden（11ms，openresty 边缘层）
- **真相**：每个 wdkbby 模块调用前必须先调 `getAppConfig()` 建立 app 级权限 token
- **修复**：登录后补回 `getAppConfig()` + `switchLang()` 预热（我重构时误删了）
- **来源**：BIT101 `DefaultCoursesRepo` 每个数据调用前都先 `getAppConfig()`

### 11. 一串字段名 / 请求参数对不上
- **TermDto**：学期代码是 `DM` 不是 `XNXQDM`，显示名是 `MC` 不是 `XNXQMC`（学期实体和课表行是不同子系统，课表行才用 XNXQDM）
- **cxzkbrq.do**：请求参数要带 **`"ZC":"1"`**（周次=1）服务器才返回每天日期 `RQ`；只发 `XNXQDM` 只返回 `[{XQ:1},...,{XQ:7}]` 没有日期
- **学期锚点简化**：既然 ZC=1 直接给出第 1 周日期，取 `XQ==1`（周一）的 `RQ` 就是学期开始日，不用反推

## 最终成功的完整调用链

```
GET  jxzxehallapp/jwapp/sys/wdkbby/*default/index.do
       └─302→ sso.bit.edu.cn/cas/login?service=<jxzxehall callback>?sessionToken=<nonce>  [200, CAS HTML]
POST sso.bit.edu.cn/cas/login?service=...   [AES-ECB 密码 + flowkey + croypto + 加密的"{}"]
       └─302→ jxzxehall callback?ticket=ST-...  └─302→ jwapp index  [200, 登录成功，cookie 落地]
GET  jwapp/sys/funauthapp/api/getAppConfig/wdkbby-5959167891382285.do   [200, 建权限]
GET  jwapp/i18n.do?appName=wdkbby&EMAP_LANG=zh   [200, 设语言]
GET  jwapp/sys/wdkbby/modules/jshkcb/dqxnxq.do   [200, 当前学期 2025-2026-2]
POST jwapp/sys/wdkbby/wdkbByController/cxzkbrq.do  {XNXQDM,ZC:1}  [200, 第1周日期]
POST jwapp/sys/wdkbby/modules/xskcb/cxxszhxqkb.do  {XNXQDM}  [200, 13 门课]
```

## 经验教训

1. **"单测全绿"只证明代码符合我们的假设，不证明假设正确。** 涉及外部协议的功能，真机/真服务验证不可省略。
2. **抓真实流量是定位协议 bug 的唯一可靠手段。** OkHttp BODY 日志 + adb logcat 把 11 个坑逐个照出来。注意 application vs network interceptor 对 Cookie 的可见性差异。
3. **参考开源实现要看"它实际怎么发请求"，不只是看数据结构。** BIT101 的 AESUtils（ECB）、Options（双 host）、CoursesRepo（每次先 getAppConfig）都是真机验证过的金线索——但 AGPL 只能观察不能抄。
4. **错误信息的演进就是进度条。** 从"找不到 execution"→"salt 长度错"→"密码错误"→"403"→"字段缺失"→"成功"，每个新错误都意味着前一个坑填平了，离终点更近一步。
5. **HTTP 标准的暗坑**：302 对 POST 降级为 GET；Retrofit 非 2xx 走 errorBody()；这些都是"理论上知道、实战才痛"的细节。
