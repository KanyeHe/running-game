# HTML5 网页版

当前主要前端。浏览器原生 HTML/CSS/ES Modules，没有 UI 框架和运行时第三方依赖。`model`、`Selection`、`Transport` 从原有 TypeScript 源码构建共享，出牌始终由 Java 服务重新校验。

## 最快运行

推荐直接在仓库根目录执行 `docker compose up --build --wait --wait-timeout 300`。H5、Java 的构建和 MySQL/Redis 启动全部在 Docker 内完成，打开 `http://localhost:8080/` 即可。详见 [Docker 说明](../docs/docker.md)。

以下为不用 Docker 的备选方式：

仓库已提交可运行的 H5 静态产物，启动 Java 服务即可，不需要 Cocos，也不需要先运行 npm：

```powershell
# 仓库根目录
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-h5.ps1 -Jdk D:\DevTools\jdk-25.0.4.1
```

启动完成打开 `http://localhost:8080/`。第一次要下载 Maven/Java 依赖，默认 memory 模式无需数据库。也可在 `server` 执行 `mvnw.cmd spring-boot:run`。Docker Compose 提供同样的 H5 首页。

## 一个人测试四人联机

打开 `http://localhost:8080/test.html`：

1. 页面创建四个 iframe，每个有独立 profile 和登录令牌，连接同一个真实后端。
2. 第一位自动建房，其余三位自动加入。建房稍慢时加入会短暂重试。
3. 四个窗口同步完成后点「全部准备」。并发准备的过期版本会在同步快照后重试。
4. 在当前出牌者窗口点「提示」和「出牌」，可手动轮流操作四个身份，完成整局。
5. 点击「新建测试桌」生成四个新身份与新房间；刷新页面会恢复原测试身份和房间。

四窗口使用真实服务端发牌和私有快照，各个 frame 只显示本人的牌。测试者可以查看四个窗口是此本地联调工具的用途。没有在前端模拟另一套游戏规则，也没有自动代打。

单窗口中创建/加入房间，复制邀请链接给其他浏览器。普通新标签页通常生成独立身份，刷新保留该标签的身份；浏览器“复制标签页”可能复制 sessionStorage，若出现相同身份，点连接设置里的「更换测试身份」。四窗口页明确命名 profile，不受此影响。

## 修改和构建

```sh
cd web
npm install
npm run build
npm test
```

`build` 将 `src` 静态页面及三个共享 TS 模块输出到 `dist`，同时同步到 `server/src/main/resources/static`。不要单独手改生成文件。共享 TypeScript 改动也要重新构建。

没有网络但已安装 TypeScript 5.9.3 时，可将 `TYPESCRIPT_PATH` 设置为其模块目录，然后运行 `node web/scripts/build.cjs`。其他项目的文件仅读取，不会修改。

## 仅预览页面

```sh
cd web
npm run preview
```

打开 `http://127.0.0.1:5173`。这是静态预览，默认连接同主机的 8080 服务端；没有 Java 服务时会明确显示未连接，不能开局。连接设置可指定另一后端地址（HTTP/HTTPS）；对应来源须加入服务端 ALLOWED_ORIGINS。正式同地址运行不需要跨域设置。

## 已覆盖的界面

- 创建/加入、两套积分方案、准备和下一局、邀请链接。
- 响应式四人牌桌，自己的位置始终在下，当前回合、在线状态、手牌数与积分。
- 手牌置灰/高亮、点击抬起、组合约束、提示循环（含癞子解释）、合法出牌。
- 服务端时间校正倒计时、自动跳过提示、对局记录、炸弹积分动画和结算对话框。
- 自动重连、刷新恢复、独立测试身份、原始请求重试与版本同步。

游客昵称目前仅在自己的页面显示；其他席位用玩家编号。H5 没有实现新的微信登录或发布流程。

## 本次验证范围

H5 构建、六组 Node 回归测试、共享 TypeScript 类型检查、静态 HTTP 200 已通过。测试覆盖 DOM 控件绑定、选牌/提示/出牌/结算、四身份隔离、刷新恢复和准备冲突重试。当前没有可用浏览器自动化会话，无法进行实际浏览器渲染 QA；Java 依赖下载仍受阻，真实后端四人联机未执行。
