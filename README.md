# 泸州跑得快

四人实时联机小游戏 MVP 源码，娱乐积分。**当前优先开发 HTML5 网页版，玩法和联机稳定后再迁移微信小游戏。**
Java 服务端权威校验；H5 和保留的 Cocos 客户端共用网络协议与候选选牌逻辑。

已完成编译排查与职责拆分，单方法最多 35 行并提供自动检查。修复与设计说明见
[维护说明](docs/refactoring.md)，最新验证结果见 [验证记录](docs/test-plan.md)。

## 一键验证

先将 `JAVA_HOME` 和 `PATH` 指向 JDK 25，然后在根目录执行：

```sh
sh scripts/verify.sh
```

该命令检查方法行数、构建 H5、执行前端测试并通过 Maven Wrapper 构建与测试服务端。
Docker 可用时实际运行 MySQL/Redis 集成测试；Cocos/微信打包需 Creator 环境。

## 直接测试 H5

**推荐：全 Docker 启动，宿主机无需 JDK/Node/Maven。** 安装并启动 Docker Desktop（Linux 容器）后，在本项目根目录运行：

```sh
docker compose up --build --wait --wait-timeout 300
```

完成后打开 `http://localhost:8080/` 或四人联机测试页 `http://localhost:8080/test.html`。应用会自动构建
H5 和 Java 镜像，并等待 MySQL、Redis
就绪。构建分步操作、可选配置和镜像导出见 [Docker 运行说明](docs/docker.md)。Compose 配置检查已通过；完整应用镜像构建与 Compose 启动未在本次执行。

以下为不使用 Docker 的本地 Java 运行方式：

仓库已包含 H5 静态产物，无需安装 Cocos 或先安装 Node 依赖。在仓库根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-h5.ps1 -Jdk D:\DevTools\jdk-25.0.4.1
```

将 JDK 路径改为自己的 JDK 25。Java 服务启动成功后打开：

- 单窗口牌桌：`http://localhost:8080/`
- 一个人测试四人联机：`http://localhost:8080/test.html`，等待四个身份入座后点击「全部准备」。

首次启动需要下载 Maven 和 Java 依赖，内存模式不需要
MySQL/Redis；服务停止后牌局和游客会话消失。本次已实际运行 Spring Boot 四客户端 WebSocket 联机测试；完整验收范围见验证记录。

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

默认 `memory` 模式不需要 MySQL/Redis；重启会丢失会话和房间。健康检查：
`GET http://localhost:8080/api/health`。首次运行 Maven Wrapper 会下载并校验 Maven 3.9.11，须有 Maven Central 网络访问。Wrapper 使用项目设置和项目 `.tools/m2` 缓存，不依赖电脑上的旧 Maven 镜像设置。

## Docker 部署

本地和服务器使用根目录同一份 `docker-compose.yml`。宿主机只需 Docker Engine / Docker Desktop（Linux 容器）和 Compose v2.20+，无需安装 Java、Maven、Node 或 Cocos。

| 服务 | 作用 | 宿主机端口 |
| --- | --- | --- |
| server | Java 25 服务、H5 页面、HTTP API 和 WebSocket | 默认 8080 |
| mysql | 用户、房间快照、对局及积分审计，MySQL 8.4 | 不映射 |
| redis | 缓存与在线状态，Redis 7.4，启用 AOF | 不映射 |

默认启用 `persistent` 模式，MySQL 为恢复来源，Redis 为辅助缓存，数据分别保存在 Docker 数据卷中。

### 数据库和表什么时候初始化

**数据库和账号由 MySQL 容器首次初始化时创建；业务表由 Java `server` 应用启动时创建。** 两者是两个独立阶段，无需手工导入 SQL。

启动顺序如下：

1. **MySQL 首次使用空数据卷启动**：官方镜像读取 Compose 中的 `MYSQL_DATABASE=paodekuai`、`MYSQL_USER=game`、`MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD`，创建 `paodekuai` 数据库、应用账号及相应权限。已有数据目录时保留原数据库，不重新初始化账号或密码。
2. **MySQL 健康检查通过**：Compose 使用 `game` 账号连接 `paodekuai` 并执行 `SELECT 1`。这一步只检查数据库连接，不检查业务表是否存在。
3. **Java 应用启动**：Compose 为 `server` 设置 `SPRING_PROFILES_ACTIVE=persistent`。该配置在 [application.yml](server/src/main/resources/application.yml) 中启用：

   ```yaml
   spring:
     sql:
       init:
         mode: always
   ```

4. **Spring Boot 执行建表 SQL**：读取打包在应用 classpath 中的 [schema.sql](server/src/main/resources/schema.sql)，创建缺失的业务表。脚本执行属于应用启动阶段；镜像构建只将该脚本打包，不会连接正式部署数据库建表。SQL 执行失败时，按当前默认配置应用启动失败，原因可在 `server` 日志中查看。[Spring Boot 初始化机制](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html)
5. **启动完成后处理业务**：游客登录、创建房间及对局操作产生实际数据。当前没有用于预填玩家或对局的 `data.sql`。

当前自动创建 8 张表：

| 表 | 作用 |
| --- | --- |
| users | 游客身份与会话令牌 |
| rooms | 房间版本及完整恢复快照 |
| matches | 对局记录 |
| player_results | 玩家手牌余量与积分结果 |
| play_records | 出牌及状态机事件记录 |
| operations | 操作请求、序号和版本审计 |
| score_ledger | 最终积分流水 |
| bomb_ledger | 炸弹积分与冲正流水 |

**如果只启动了 MySQL 容器，看到数据库但没有业务表属于正常情况。** 还需要启动并等待 `server` 应用成功完成初始化；只看到 MySQL 为 healthy 不能判断建表完成。

在项目部署目录确认状态和表结构：

```sh
# 确认 server 是否已启动；失败时查看初始化错误
docker compose ps
docker compose logs --tail=100 server

# 交互输入 .env 中的 DB_PASSWORD，检查业务表
docker compose exec mysql mysql -u game -p paodekuai -e "SHOW TABLES;"
```

每次 `persistent` 应用启动都会执行该脚本，但当前 SQL 全部使用 `CREATE TABLE IF NOT EXISTS`，已存在的表会保留，不会清空已有数据。
**这不等于自动升级旧表结构**：已有表增加字段、修改类型或索引，需要另行提供迁移 SQL；当前未引入 Flyway/Liquibase。不要通过删除数据卷解决结构升级问题。
直接运行 Java 服务默认使用 `memory` 模式，禁用 JDBC 数据源自动配置，因此不会连接 MySQL 或执行建表脚本。

### 本地 Docker 部署

1. 启动 Docker Desktop，确认 Docker 服务与 Compose 可用：

   ```sh
   docker version
   docker compose version
   ```

2. 进入项目根目录，首次部署复制环境配置；已有 `.env` 时编辑原文件，不要覆盖：

   ```sh
   cd /path/to/running-game
   cp .env.example .env
   ```

   Windows PowerShell 可用 `Copy-Item .env.example .env`。本地测试可先使用默认配置。若 8080 被占用，将 `.env` 改为：

   ```dotenv
   APP_PORT=8090
   ALLOWED_ORIGINS=http://localhost:8090,http://127.0.0.1:8090
   ```

3. 检查配置并构建启动：

   ```sh
   docker compose config --quiet
   docker compose up --build --wait --wait-timeout 300
   ```

   Dockerfile 自动安装前端依赖、检查共享类型、构建 H5、执行测试并打包 Java 服务；Compose 等待 MySQL、Redis 就绪后启动应用。
   首次需要访问 Docker Hub、npm registry 和 Maven Central。`--wait` 会后台运行并等待服务健康，300 秒限制的是启动健康等待，不是镜像构建时间。

4. 查看状态并访问页面：

   ```sh
   docker compose ps
   curl http://localhost:8080/api/health
   ```

   - H5 牌桌：`http://localhost:8080/`
   - 四身份联机测试：`http://localhost:8080/test.html`

   若修改了端口，替换地址中的 `8080`。四窗口页自动创建/加入房间，四个身份同步后点击「全部准备」。

### 服务器 Docker 部署

首次建议将当前源码上传服务器，在服务器上构建。服务器需预先安装并启动 Docker Engine 和 Compose 插件，确认 `docker version`、`docker compose version` 可用。

1. 在本地项目根目录上传源码。将 `deploy@服务器IP` 替换为实际 SSH 用户与地址：

   ```sh
   ssh deploy@服务器IP 'mkdir -p ~/running-game'

   rsync -av \
     --exclude='.git' \
     --exclude='.env' \
     --exclude='.tools' \
     --exclude='.idea' \
     --exclude='node_modules' \
     --exclude='target' \
     --exclude='dist' \
     ./ deploy@服务器IP:~/running-game/
   ```

   此上传方式适用于 macOS/Linux。Windows 可用 SFTP 上传相同源码目录，并排除上述缓存和本地配置。
   上传必须包含当前修复后的文件；如果修改尚未提交并推送，仅在服务器克隆旧仓库不会包含修复。

2. 登录服务器，首次创建服务器自己的 `.env`：

   ```sh
   ssh deploy@服务器IP
   cd ~/running-game
   cp .env.example .env
   chmod 600 .env
   nano .env
   ```

   配置示例（密码和 IP 均须替换）：

   ```dotenv
   APP_PORT=8080
   APP_IMAGE=running-game/server
   APP_TAG=0.1.0
   DB_PASSWORD=replace-with-a-strong-database-password
   MYSQL_ROOT_PASSWORD=replace-with-a-different-strong-root-password
   ALLOWED_ORIGINS=http://服务器IP:8080
   ```

   `ALLOWED_ORIGINS` 使用浏览器实际访问的来源，包含协议、域名或 IP、端口，末尾不加 `/`；多个来源用逗号分隔。

3. 拉取依赖镜像、构建并启动：

   ```sh
   docker compose config --quiet
   docker compose pull mysql redis
   docker compose build --pull server
   docker compose up --wait --wait-timeout 300
   ```

4. 检查服务器内部访问：

   ```sh
   docker compose ps
   docker compose logs --tail=100 server
   curl http://127.0.0.1:8080/api/health
   ```

5. 在云服务器安全组和系统防火墙放行应用的 TCP 8080（自定义端口则放行对应端口），然后访问：

   - `http://服务器IP:8080/`
   - `http://服务器IP:8080/test.html`

   MySQL 3306 和 Redis 6379 不需要对公网开放。

### 正式域名与 HTTPS

以 `https://game.example.com` 为例：

1. 将域名解析到服务器。
2. 在 Nginx 或现有网关配置 HTTPS，反向代理到应用的 8080；同机 Nginx 可代理 `127.0.0.1:8080`，容器网关应使用可达的容器网络地址。
3. 为 `/ws` 配置 WebSocket Upgrade 转发和合适的长连接超时。
4. 在 `.env` 设置 `ALLOWED_ORIGINS=https://game.example.com`，再执行：

   ```sh
   docker compose up --wait --wait-timeout 300
   ```

H5 自动使用同源 API 和 WebSocket，HTTPS 页面使用 WSS。域名入口需要放行 TCP 80/443；应用 8080 可限制为仅代理访问。

### 更新、日志与停止

以下命令在部署目录执行，本地和服务器通用：

```sh
# 上传新源码后，重新构建并更新应用
# rsync 排除了 .env，因此保留服务器现有配置
docker compose build server
docker compose up --wait --wait-timeout 300

# 查看运行状态和应用日志
docker compose ps
docker compose logs -f --tail=100 server

# 排查数据库或缓存启动失败
docker compose logs --tail=100 mysql redis

# 停止并删除容器、网络，保留数据卷
docker compose down

# 再次启动已有镜像
docker compose up --wait --wait-timeout 300
```

- 数据卷为 `running-game_mysql-data` 和 `running-game_redis-data`。正常停止不要加 `-v`，否则会删除持久化数据；更新前备份重要数据库数据。
- MySQL 初始化后，修改 `.env` 不会自动修改数据库内的账号密码。须先正确变更数据库密码，再同步应用配置。
- 当前仅支持一个 `server` 实例，同房间锁在单进程内，不能使用 `--scale server=多份`。更新应用会短暂断开连接，客户端按重连协议恢复。
- 如果在本地构建镜像再上传，必须匹配服务器 CPU 架构。Apple Silicon 常为 ARM64，常见 x86 服务器为 AMD64；跨架构需指定目标平台或构建多平台镜像，详见 [Docker 多平台构建说明](https://docs.docker.com/build/building/multi-platform/)。在目标服务器构建可避免直接导入架构不匹配的本地镜像。

**验证范围：** 已验证 Compose 配置、Java 打包及真实 MySQL/Redis、WebSocket 集成测试；完整应用镜像构建、Compose 启动和正式域名代理尚未实测。
镜像导出/导入、分步构建及其他配置见 [Docker 运行说明](docs/docker.md)。

## 前端

**当前使用 H5**：源码在 `web`，运行和四窗口测试说明见 [web/README.md](web/README.md)。修改页面后，在
`web` 执行 `npm install`、`npm run build`、`npm test`。构建会同步到 Java 静态资源目录，再启动/打包服务即可。Docker
镜像也包含这些产物。

以下 Cocos 配置为后续迁移参考，当前阶段不用安装或打包：

使用 Cocos Creator **3.8.8** 打开 `client`，打开 `assets/scenes/Main.scene`。在 `GameTable.ts` 中设置
`serverUrl`（默认本机 8080），预览场景。四个浏览器独立会话分别登录，创建/加入同一个至少六位的房间号，四人点击准备后开局。

完整操作与微信构建见 [client/README.md](client/README.md)。没有参考截图可供复刻，当前是程序绘制的绿色牌桌与卡牌，不依赖外部图片。当前机器未发现
Creator，场景导入、引擎类型检查和微信打包未执行。

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

实现了所有指定牌型及癞子候选、实体牌首出限制、强制出牌、自动跳过、多圈出牌、赢家首出限制、两套积分档位、炸弹即时记账与冲正、房间串行化/版本/幂等、私有视图、重连快照、持久化源码，以及
Cocos 四人布局、选牌/置灰/提示循环/倒计时/结算/重连和积分动画。

当前全部联机运行使用**明确标记的 `demo-v1` 策略**。癞子、炸弹比较、2 炸弹定义、连续区间边界、附件比较、跨牌型提示顺序、超时及黑桃
3 落入弃牌时的处理仍待产品确认，见 [open-questions.md](docs/open-questions.md)。它们不是已确认的地方规则。

下一阶段：先用 H5 完成真实四人联机、整局、刷新/断线恢复和计分联调；确认地方规则并稳定后，再迁移微信小游戏和正式登录。当前没有上线发布。
