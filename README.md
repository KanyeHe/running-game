# 泸州跑得快

四人实时联机小游戏 MVP 源码，娱乐积分。**当前优先开发 HTML5 网页版，玩法和联机稳定后再迁移微信小游戏。** Java 服务端权威校验；H5 和保留的 Cocos 客户端共用网络协议与候选选牌逻辑。

**当前交付状态：核心规则、房间层和前端逻辑已实测，H5 静态构建成功；完整 Spring 应用和数据库联机尚未验证，不能视为已通过完整 MVP 验收。** 详见 [验证记录](docs/test-plan.md)。

## 直接测试 H5

**推荐：全 Docker 启动，宿主机无需 JDK/Node/Maven。** 安装并启动 Docker Desktop（Linux 容器）后，在本项目根目录运行：

```sh
docker compose up --build --wait --wait-timeout 300
```

完成后打开 `http://localhost:8080/` 或四人联机测试页 `http://localhost:8080/test.html`。应用会自动构建 H5 和 Java 镜像，并等待 MySQL、Redis 就绪。构建分步操作、可选配置和镜像导出见 [Docker 运行说明](docs/docker.md)。当前环境缺少 Docker，配置尚未完成实际容器验证。

以下为不使用 Docker 的本地 Java 运行方式：

仓库已包含 H5 静态产物，无需安装 Cocos 或先安装 Node 依赖。在仓库根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-h5.ps1 -Jdk D:\DevTools\jdk-25.0.4.1
```

将 JDK 路径改为自己的 JDK 25。Java 服务启动成功后打开：

- 单窗口牌桌：`http://localhost:8080/`
- 一个人测试四人联机：`http://localhost:8080/test.html`，等待四个身份入座后点击「全部准备」。

首次启动需要下载 Maven 和 Java 依赖，内存模式不需要 MySQL/Redis；服务停止后牌局和游客会话消失。当前代理环境仍无法下载依赖，因此本次没有实际启动完整 Java 服务。前端静态可访问不代表联机服务已经可用。

## 项目结构

- `server/src/main/java/cn/luzhou/rules`：独立纯 Java 规则引擎、按牌型剪枝枚举、策略接口。
- `server/src/main/java/cn/luzhou/game`：发牌、多圈状态机、结算、炸弹冲正、超时策略。
- `server/src/main/java/cn/luzhou/room`：房间锁、准备、版本校验、幂等回执、私有视图。
- `server/src/main/java/cn/luzhou/api`：HTTP 游客登录、WebSocket 请求处理。
- `server/src/main/java/cn/luzhou/store`：内存适配器与 MySQL/Redis 持久化适配器。
- `client/assets`：Cocos 场景、完整程序化牌桌、选牌、提示、网络重连。
- `web/src`：HTML5 响应式牌桌和四身份测试页；`web/scripts`：静态构建/预览；`web/tests`：H5 交互回归测试。
- `server/src/main/resources/static`：由 H5 构建生成的浏览器产物，Java 服务同地址提供。
- `deploy`：JDK 25 镜像和 MySQL 8.4 / Redis 7.4 Compose。
- `scripts`：无需 Maven 的核心验证、客户端逻辑测试、场景生成。
- `docs`：规则、协议、架构、数据库、未决项和验证结果。

## 本地运行

所有 Java 编译及运行均要求 **JDK 25**（Docker 方式已在镜像内提供）。采用 Spring Boot **3.5.16**；[官方兼容说明](https://docs.spring.io/spring-boot/3.5/system-requirements.html)支持 Java 25。

Windows PowerShell：

```powershell
$env:JAVA_HOME='D:\DevTools\jdk-25.0.4.1' # 改为自己的 JDK 25 路径
cd server
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Linux/macOS：

```sh
export JAVA_HOME=/path/to/jdk-25
cd server
sh mvnw verify
sh mvnw spring-boot:run
```

默认 `memory` 模式不需要 MySQL/Redis；重启会丢失会话和房间。健康检查：`GET http://localhost:8080/api/health`。首次运行 Maven Wrapper 会下载并校验 Maven 3.9.11，须有 Maven Central 网络访问。Wrapper 使用项目设置和项目 `.tools/m2` 缓存，不依赖电脑上的旧 Maven 镜像设置。

## Docker Compose

在项目根目录：

```sh
docker compose config --quiet
docker compose up --build --wait --wait-timeout 300
docker compose logs -f server
docker compose down
```

默认启用 `persistent` 模式，MySQL 为恢复来源，Redis 为辅助快照/在线/会话缓存。两个数据卷保留历史，H5 和 Java 服务合并为应用镜像。仅支持一个游戏服务实例。完整构建及部署步骤见 [docs/docker.md](docs/docker.md)。

## 前端

**当前使用 H5**：源码在 `web`，运行和四窗口测试说明见 [web/README.md](web/README.md)。修改页面后，在 `web` 执行 `npm install`、`npm run build`、`npm test`。构建会同步到 Java 静态资源目录，再启动/打包服务即可。Docker 镜像也包含这些产物。

以下 Cocos 配置为后续迁移参考，当前阶段不用安装或打包：

使用 Cocos Creator **3.8.8** 打开 `client`，打开 `assets/scenes/Main.scene`。在 `GameTable.ts` 中设置 `serverUrl`（默认本机 8080），预览场景。四个浏览器独立会话分别登录，创建/加入同一个至少六位的房间号，四人点击准备后开局。

完整操作与微信构建见 [client/README.md](client/README.md)。没有参考截图可供复刻，当前是程序绘制的绿色牌桌与卡牌，不依赖外部图片。当前机器未发现 Creator，场景导入、引擎类型检查和微信打包未执行。

## 测试

```powershell
# 不依赖网络/Maven：100 个固定种子牌局及规则、状态机、记账检查
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-core.ps1 -Jdk D:\DevTools\jdk-25.0.4.1 -Seeds 100

# JUnit 5、房间并发、四个真实 WebSocket 客户端、Testcontainers
cd server
.\mvnw.cmd verify

# 前端核心类型检查及逻辑测试
cd ../client
npm install
npm run typecheck:core
node ../scripts/test-client.cjs
# 首次由 Creator 导入，生成 temp/tsconfig.cocos.json 后
npm run typecheck
```

MySQL/Redis Testcontainers 测试没有 Docker 时会明确跳过；WebSocket 测试使用内存模式。核心模拟异常会打印随机种子及完整操作日志。

## 已实现与待完成

实现了所有指定牌型及癞子候选、实体牌首出限制、强制出牌、自动跳过、多圈出牌、赢家首出限制、两套积分档位、炸弹即时记账与冲正、房间串行化/版本/幂等、私有视图、重连快照、持久化源码，以及 Cocos 四人布局、选牌/置灰/提示循环/倒计时/结算/重连和积分动画。

当前全部联机运行使用**明确标记的 `demo-v1` 策略**。癞子、炸弹比较、2 炸弹定义、连续区间边界、附件比较、跨牌型提示顺序、超时及黑桃 3 落入弃牌时的处理仍待产品确认，见 [open-questions.md](docs/open-questions.md)。它们不是已确认的地方规则。

下一阶段：先用 H5 完成真实四人联机、整局、刷新/断线恢复和计分联调；确认地方规则并稳定后，再迁移微信小游戏和正式登录。当前没有上线发布。
