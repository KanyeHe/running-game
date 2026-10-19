# Docker 镜像构建与 Compose 运行

## 一条命令启动

宿主机只需 **Docker Engine / Docker Desktop（Linux 容器模式）及 Docker Compose v2.20+**，无需安装 Java、Maven、Node 或 Cocos。所有命令在项目根目录执行。

```powershell
cd E:\vscode_workspace\running-game
docker compose up --build --wait --wait-timeout 300
```

`--wait` 会后台运行并等待容器达到运行/健康状态，而不是让终端持续占用。首次需要访问 Docker Hub、npm registry、Maven Central，耗时取决于下载速度。等待时间只约束启动健康等待，不限制镜像构建所需时间。[Docker 官方说明](https://docs.docker.com/reference/cli/docker/compose/up/)

全部健康后打开：

- H5 牌桌：<http://localhost:8080/>
- 四身份联机测试：<http://localhost:8080/test.html>
- 应用 HTTP 健康检查：<http://localhost:8080/api/health>

四窗口页会自动建房/加入；四个窗口同步后点击「全部准备」，即可在四个玩家的窗口中轮流操作。

## 容器及镜像

| Compose 服务 | 镜像 | 内容 | 宿主机端口 |
|---|---|---|---|
| server | 本项目构建的 `running-game/server:0.1.0` | Java 25 服务端、H5 静态页面、HTTP/WS | 默认 8080 |
| mysql | 官方 `mysql:8.4` | 用户、房间快照、对局/操作/积分审计 | 不映射，容器内部 3306 |
| redis | 官方 `redis:7.4-alpine` | 会话/房间辅助缓存、在线标记；启用 AOF | 不映射，容器内部 6379 |

MySQL、Redis 直接拉取官方镜像，不需要重新编译。H5 与 Java 合并进一个应用镜像，浏览器同地址访问 API 和 WebSocket，无需额外 Nginx 服务。

`deploy/Dockerfile` 三阶段：

1. Node 24：`npm ci` 按锁文件安装 TypeScript；重新构建 H5 和共享模块，执行共享类型检查及 H5 回归测试。
2. Maven 3.9 / JDK 25：把本次生成的 H5 放入静态资源，运行 `mvn verify`，编译 JRE 健康检查小程序。
3. Temurin JRE 25：只保留应用 jar 和健康检查 class，以非 root UID 运行。镜像内不需要 npm/Maven/源码或 curl。

构建默认运行 Java 普通单测和内存模式的真实 WebSocket 测试，不跳过测试。Testcontainers 因 Docker 构建阶段没有 Docker daemon/socket，通常会明确跳过；运行时容器健康检查不等于 Testcontainers 测试通过。

## 分步构建与启动

如需分别看下载、构建和启动日志：

```sh
docker compose version
docker compose config --quiet
docker compose pull mysql redis
docker compose build --pull server
docker compose up --wait --wait-timeout 300
docker compose ps
docker compose images
```

也可独立构建应用镜像（构建上下文必须是项目根目录）：

```sh
docker build -f deploy/Dockerfile -t running-game/server:0.1.0 .
```

Dockerfile 的 BuildKit 缓存保存 npm/Maven 下载结果，修改代码后重新构建会复用这些缓存。`.dockerignore` 排除本机依赖、旧产物和本地 `.env`；容器内始终从 H5 源码构建，而不是复制可能过期的本机 dist。

## 可选配置

默认值可以直接用于本地测试；需要改端口/镜像名/数据库密码时，在首次启动前复制 `.env.example` 为 `.env`：

```powershell
Copy-Item .env.example .env
```

例如把 `.env` 中 `APP_PORT=8080` 改为 `APP_PORT=8090`，重新运行 Compose，浏览器访问 `http://localhost:8090/`。镜像内部端口仍为 8080。

`DB_PASSWORD` 同时传给 MySQL 和应用；`MYSQL_ROOT_PASSWORD` 是数据库管理员密码。MySQL 已初始化数据卷后，修改环境变量不会自动修改数据库内的现有账号密码，须使用原密码或在数据库内正确变更，再同步 `.env`。不要把改密码问题通过删除已有数据卷处理。

默认仅应用端口对外映射。其他局域网设备可打开 `http://开发电脑的局域网IP:8080/`，与同地址 API/WS 通信。若电脑防火墙有入站限制，需要在宿主机允许所选应用端口。独立前端域名/端口则用 `ALLOWED_ORIGINS` 设置实际来源。

## 查看状态、更新、停止

```sh
# 查看状态和应用日志
docker compose ps
docker compose logs -f --tail=100 server

# 数据库或 Redis 启动失败时
docker compose logs --tail=100 mysql redis

# 更新源码后重建应用，再启动
docker compose build server
docker compose up --wait --wait-timeout 300

# 停止并删除容器、网络，保留对局数据
docker compose down

# 再次运行已有镜像
docker compose up --wait --wait-timeout 300
```

数据卷为 `running-game_mysql-data` 和 `running-game_redis-data`。MySQL 是恢复来源，Redis 丢失可由 SQL 恢复。不要在正常停止命令后加 `-v`，那会删除持久数据。

本应用仅支持一个 server 实例，同房间串行锁在单进程内；不要使用 `--scale server=多份`。

## 保存镜像，到另一台电脑运行

构建并拉取完成后，默认镜像名下可导出三份运行镜像到同一个文件：

```sh
docker save -o running-game-images.tar running-game/server:0.1.0 mysql:8.4 redis:7.4-alpine
```

将 `running-game-images.tar`、根目录 `docker-compose.yml` 和使用中的 `.env` 一起复制到另一台相同 CPU 架构的 Docker 主机，放在同一目录：

```sh
docker load -i running-game-images.tar
docker compose up --no-build --pull never --wait --wait-timeout 300
```

如已自定义 APP_IMAGE/APP_TAG，保存、加载和 `.env` 必须使用相同名称。镜像导出不包含运行中的 MySQL 对局数据；另一台机器会初始化新数据库。

## 兼容旧命令

原 `deploy/docker-compose.yml` 现在引用根目录唯一配置，避免两份定义产生差异。旧的 `docker compose -f deploy/docker-compose.yml up --build --wait` 仍可使用；引用语法要求 Compose 2.20+。[官方 include 说明](https://docs.docker.com/reference/compose-file/include/)

## 本次验证边界

本次已生成全部 Docker/Compose 文件、锁文件及使用步骤，并用 JDK 25 编译健康检查程序；H5 构建/回归测试和 YAML 静态解析另外执行并记录于测试文档。当前执行环境没有 `docker` 命令，且外部依赖下载受限，**没有实际生成应用镜像，也没有实际运行 Compose**；不能将这些配置视为已通过真实容器联调。首次在你的 Docker 环境运行仍可能暴露尚未执行的完整 Java 构建或运行问题，失败时按上方命令查看日志。
