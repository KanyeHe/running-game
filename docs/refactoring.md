# 编译排查与可维护性约束

本次以根目录 `requirements.md` 为业务依据，保留 H5 优先和 `demo-v1` 未确认策略的边界。

## 已定位的问题

- 宿主机默认 Java 8，项目要求 JDK 25。Wrapper 现在在下载和编译前明确检查版本，不降低编译目标。
- `PersistenceTest` 同时通配符导入两个 `Container`，导致测试编译失败；改用显式导入。
- Unix Wrapper 只使用 `sha512sum`，macOS 默认没有该工具；增加 `shasum -a 512` 回退。
- Wrapper 依赖当前目录；现在显式指定自身目录的 `pom.xml`，可从仓库根目录调用。
- 四窗口测试依赖源码空格，格式化就会误报；改为执行脚本，断言身份隔离、房间一致、来源校验和准备消息。
- H5 原构建仅转译 TypeScript，不检查类型；现在先检查共享核心模块，错误时不会复制产物。
- Transport 主动关闭后仍报告已连接，旧连接迟到事件可修改新连接状态；关闭时清理认证，并忽略旧连接事件。
- WebSocket 收到 JSON `null` 或空值业务参数会走内部错误；现在统一返回 `INVALID_REQUEST`，并有真实连接回归测试。

## 代码逻辑与职责

1. `SocketConfig` 负责协议解析、Validation、认证和发送；请求只表达操作意图。
2. `RoomService` 在房间锁内读取并复制快照，先处理幂等回执，再校验版本，调用业务变更，持久化成功后广播。
3. `RoomCommandHandler` 负责创建、加入、准备、开局和出牌，不负责网络和数据库。
4. `RoomViewFactory` 投影公开信息与当前玩家自己的手牌、合法候选，禁止直接返回完整 `Room`。
5. `Game` 使用连续三次无法压制结束一轮；成功出牌重置计数，允许同一玩家在同一轮多次出牌。有人出完手牌立即结算。
6. `CardCombinationFinder` 按点数模板剪枝，回溯分配实体牌及癞子替代身份；不可变 `Template` 保存同一搜索分支的牌型参数。
7. `BombScoreLedger` 通过追加反向流水撤销同轮旧炸弹分；`JdbcRedisStore` 在同一 SQL 事务保存快照与审计，提交后刷新 Redis。
8. H5/Cocos 分别拆分席位、手牌、操作和结算渲染，共用 Selection/Transport；合法候选仍由服务端决定。

## 七项设计原则的落点

| 原则 | 本次处理 |
| --- | --- |
| 单一职责 | 分离房间编排、业务变更、私有视图；分拆网络处理和渲染职责 |
| 开闭原则 | 保留 Wildcard/Bomb/Sequence/Plane/Timeout 策略，集中在 RuleConfig 装配 |
| 里氏替换 | 保持 StateStore 与 Socket 适配器契约；用内存、SQL 和浏览器测试验证替换路径 |
| 接口隔离 | 平台 Socket 仅暴露发送、关闭和必要事件；玩法策略按能力拆分 |
| 依赖倒置 | RoomService 依赖 StateStore；规则引擎保持纯 Java，finder 通过构造器注入 |
| 迪米特法则 | 网络层通过 RoomService 操作；私有视图集中投影，前端不读取内部快照 |
| 合成复用 | 使用策略、命令处理器、视图工厂及平台适配器组合，避免为复用业务引入继承树 |

原则用于控制职责与变化范围，并不表示现有全部公共状态模型已彻底封装。Room/Game 仍是当前 JSON 快照模型；修改字段需同时验证历史恢复。当前房间锁只支持单服务实例，玩法未决项见 `open-questions.md`。

## 行数与复验

所有手写 Java/TypeScript/JavaScript 方法、构造器及 JS/TS 回调最多 **35 个物理行**，从声明到结束括号计算，包含空行与注释。测试源码同样检查；生成产物从源码构建，不单独修改。不能靠压缩多条语句规避约束。

macOS/Linux 设置 `JAVA_HOME` 为 JDK 25 后，在仓库根目录执行：

```sh
sh scripts/verify.sh
```

脚本执行 npm 锁定安装、Java/JS/TS 语法树行数检查、共享核心类型检查、H5 构建、H5/客户端回归与 Maven `verify`。有 Docker 时运行 MySQL/Redis 集成测试；无 Docker 时 Testcontainers 明确跳过，须查看测试报告。Maven 报告位于 `server/target/surefire-reports`。

Cocos 全量引擎检查需由 Creator 3.8.8 导入项目生成 `temp/tsconfig.cocos.json` 后执行，核心类型检查不代替引擎检查。Windows Wrapper 已同步路径与 JDK 检查，当前 macOS 环境未执行 PowerShell 分支。
