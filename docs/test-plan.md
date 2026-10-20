# 最新验证记录

2026-10-20，macOS，Homebrew JDK 25.0.1、Node 20.20.2、TypeScript 5.9.3、Maven Wrapper 3.9.11、Docker Desktop。

## 已实际执行

| 验证 | 结果 |
| --- | --- |
| `sh scripts/verify.sh`（设置 JDK 25 后） | 成功，包含行数检查、前端构建/测试和 Maven verify |
| Maven Wrapper `verify` | BUILD SUCCESS，主源码与测试源码均按 release 25 编译，可执行 JAR 已生成 |
| JUnit | 7 项通过，0 失败，0 错误，0 跳过 |
| MySQL 8.4 / Redis 7.4-alpine Testcontainers | 真实容器运行通过，非跳过；验证用户认证、重复保存、审计去重及清除 Redis 后从 SQL 恢复 |
| Spring Boot WebSocket | 四个真实 Java HttpClient 客户端认证、进房、准备、发牌、出牌及重连通过；另测 JSON null、空值参数及畸形 JSON 返回 INVALID_REQUEST |
| RoomService | 身份、私有投影、幂等、回执冲突、并发单次提交、重启恢复和持久化失败回滚通过 |
| 独立核心验证 | `javac --release 25` 编译规则/状态机/CoreChecks，100 固定种子完整牌局，共 11,616 项检查通过 |
| H5 构建及共享核心类型检查 | 通过，产物同步到 web/dist 和服务端 static |
| H5 Node 回归 | 8 项通过，包含四身份隔离、恢复、来源校验、准备冲突重试、候选出牌和跨局结算 |
| 客户端独立回归 | 选牌、提示循环、Java key 兼容、重连原请求重放顺序、旧连接事件隔离与主动关闭认证清理通过 |
| Java / JS / TS 方法行数 | 语法树检查通过，手写源码与测试方法/构造器/JS 回调不超过 35 个物理行（包含注释和空行） |
| `docker compose config --quiet` | 通过 |
| Wrapper | macOS 下载与 SHA512 校验通过；从根目录调用成功；默认 Java 8 时按预期提示 JDK 25 并退出 |
| `git diff --check` | 通过 |

JUnit 报告：`server/target/surefire-reports/`。应用包：`server/target/paodekuai-0.1.0.jar`。

## 复验

macOS/Linux 在仓库根目录运行：

```sh
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
sh scripts/verify.sh
```

有 Docker 时须确认 PersistenceTest 没有跳过。没有 Docker 时仍可执行规则、房间、真实 WebSocket 和前端测试，容器测试会明确标记跳过。

仅运行后端：`sh server/mvnw verify`。启动内存服务：`sh server/mvnw spring-boot:run`，访问 `http://localhost:8080/` 或 `http://localhost:8080/test.html`。

## 尚未执行

- 完整应用 Docker 镜像构建与 Compose `up --build`；配置校验和 Testcontainers 通过不能代替整套部署验收。
- 人工浏览器视觉与四人真实设备整局验收。Node DOM 测试不能代替视觉和真机测试。
- Cocos Creator 场景导入、引擎全量类型检查及微信构建：本环境未提供生成的 `client/temp/tsconfig.cocos.json`，不能用核心类型检查冒充引擎验证。
- Windows PowerShell Wrapper 分支：已同步 JDK 和路径修复，当前 macOS 环境未执行。
- `demo-v1` 待确认规则的产品验收。当前测试验证实现与明确演示策略，不表示地方规则已经全部确认。

修复原因、职责与设计原则说明见 [维护说明](refactoring.md)。
