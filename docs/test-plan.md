# 验证记录与待执行项

2026-10-20 Docker 交付：新增根目录 `docker-compose.yml`、Node→Maven/JDK25→JRE25 多阶段 Dockerfile、npm 锁文件、`.env.example` 和 `docs/docker.md`。YAML 解析及服务/依赖/构建路径/卷/兼容 include 静态一致性检查通过；`npm ci --dry-run --offline` 通过，H5 构建及六组回归测试通过。健康探针用 JDK25 编译，并对本地 HTTP 桩验证 UP 成功、DOWN/503 失败。当前缺少 Docker CLI，实际 `docker compose config`、镜像构建和容器启动未执行。

2026-10-20 前端调整为 H5 优先：`web` 构建成功，静态页面 HTTP 200；六组 H5 Node 回归测试通过（含四身份隔离、恢复、准备冲突重试和页面选牌出牌/结算）。共享 TypeScript 5.9.3 类型检查通过。浏览器工具未提供可用实例，实际视觉 QA 未执行；Maven Central 仍连接失败，完整后端联机待验。具体运行入口见 `web/README.md`。以下保留前次核心/服务端验证结果，Cocos/微信打包现为后续阶段。

本次验证环境：Windows PowerShell，本机 Oracle JDK 25.0.4.1；Node 24.18.1。默认 java 命令为旧版，因此所有 Java 验证显式使用 JDK 25 路径，未降低编译目标。

## 已执行

- `scripts/test-core.ps1 -Jdk D:\DevTools\jdk-25.0.4.1 -Seeds 100`：`javac --release 25` 编译纯 Java 规则、游戏状态机与 CoreChecks 成功；**11,616 项检查通过**。包含 100 个固定种子的完整牌局，检查唯一归属、54 张守恒、出牌来源/扣牌数、结算零和及终止；异常输出种子和操作记录。
- `RoomChecks`：用本机已有 Jackson 2.21.4 / annotations 2.21 和 Spring Context 6.2.12 缓存 jar 编译运行实际房间源码，通过身份、私有视图、幂等、防篡改回执、并发单次提交、重连恢复、持久化失败回滚检查。这不等于完整 Spring Boot 依赖集验证。
- 使用另一项目已有 TypeScript **5.9.3** 编译器执行 `tsc -p client/tsconfig.core.json`：通过，仅覆盖 model/Selection/Transport，无引擎类型桩。
- `node scripts/test-client.cjs <现有typescript路径>`：通过选牌约束、灰牌、完整合法集合、提示循环和重置、Java 组合 key 兼容、重连先重发原意图再更新 seq 的回归检查。
- `scripts/create-scene.py` 生成稳定 Cocos 场景与元数据；JSON 文件可解析。未将 JSON 生成算作 Cocos 构建。

## 未通过/未执行（不得视为成功）

- Maven 完整 `test/verify`：无法解析未缓存的 Spring Boot 3.5.16 parent；下载网络连接被拒绝。已提供独立项目 Maven 设置，避开电脑旧的 HTTP 阿里镜像和不可写的外部仓库配置；离线确认所需 parent 缺失。Maven Wrapper 发行包也未下载，因此 wrapper 启动尚未验证。
- `RulesTest`、`RoomServiceTest`、`WebSocketTest`、`PersistenceTest` 的 Maven/JUnit 5 执行：均待依赖可用后运行。CoreChecks/RoomChecks 独立运行结果不能冒充 JUnit 报告。
- `WebSocketTest` 已写四个真实 Java HttpClient WebSocket 客户端的认证、进房、准备、私有发牌、出牌及恢复测试，但本次未执行。
- 没有可用 `docker` 命令，因此 Compose config、镜像构建、MySQL/Redis Testcontainers 都未执行。容器测试设置 `disabledWithoutDocker=true`，日后运行时须检查报告中是否 SKIPPED。
- 未发现 Cocos Creator，不能生成真实 temp 引擎类型、导入场景、验证 UI、构建微信小游戏。全量 `npm run typecheck` 和微信真机联调待执行。
- `npm install --offline` 没有 TypeScript 缓存；本次使用已有同版本编译器只读运行，没有向其他项目写入文件。

## 环境齐备后的验收顺序

1. JDK 25 下 `server/mvnw verify`，检查所有单测通过及容器测试状态。
2. Docker Compose `config` / `up --build`，确认健康检查；persistent 模式创建房间，出牌后重启服务，四个客户端原令牌恢复。
3. 重复同 seq/requestId、并发同版本、伪造牌集合、冒充成员、过期版本，确认不重复扣牌/结算，错误码正确。
4. 在 Creator 导入 Main，完整类型检查，四个独立会话联机。验证高亮与候选一致、两张高亮牌无法拼成非法组合、提示含癞子解释/普通响应/炸弹、跨圈轮回、自动跳过、炸弹冲正和结算。
5. Creator 微信构建及开发者工具/四机真机测试，验证 HTTPS/WSS 域名、网络切换、倒计时、后台恢复、屏幕适配和积分动画。
6. 产品确认未决策略后新增确定预期用例，替换 demo-v1；在此之前不得宣称最终地方规则已全部实现。
