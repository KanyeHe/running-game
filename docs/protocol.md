# HTTP / WebSocket 协议

`POST /api/login`，JSON `{"name":"游客"}` → `{playerId,token,name}`。`GET /api/health` 返回运行状态和策略标记。部署时为 HTTPS，WebSocket 为 WSS。

连接 `/ws` 后首先发送：

```json
{"type":"AUTH","requestId":"auth","seq":1,"expectedVersion":0,"token":"登录令牌","data":{}}
```

成功响应 `LOGIN_SUCCESS`。后续消息身份来自此连接，不接受自报 playerId：

```json
{"type":"PLAY","requestId":"唯一请求ID","seq":9,"roomId":"room-123456","expectedVersion":12,"data":{"combinationKey":"SINGLE:[2]:{}:3:1:3"}}
```

| 请求 | data / 行为 |
|---|---|
| CREATE_ROOM | 稳定的客户端房间 ID（6…64 字符）；expectedVersion=1；可设 scorePlan=low/high |
| JOIN_ROOM | 加入 roomId，不信任客户端初始版本，返回当前快照 |
| READY | 当前版本，四人都准备后服务端发牌；结算后可再次准备 |
| PLAY | 服务端合法候选的 combinationKey，重新计算并校验 |
| SNAPSHOT / HINT | 只读，返回本人的私有快照和当前合法候选，无须增加状态版本 |
| RECONNECT | 校验成员，恢复连接与当前状态；期望版本可过期 |

所有请求必须有正 seq、requestId 和 data 对象。状态请求 seq 按玩家在房间内递增。重试必须原封不动重用原请求；不得修改 expectedVersion 再冒充重试。

响应/广播顶层：`type, requestId, roomId, version, playerId, players, ready, online, policy`。游戏中另有 `hand`（只含本人）、`counts,turn,table,trick,winner,scores,deadline,serverTime,legal,events`。座位为 0…3；winner=-1 表示进行中；截止时间为 epoch 毫秒。`table=null` 表示领出。组合包括实体 ID、wildRanks、type、value、groups、bodyRank、basis。

状态提交响应为 ACK；广播 type 为原业务请求或 PLAYER_DISCONNECTED，events 提供 GAME_STARTED、PLAYED、AUTO_SKIPPED、TRICK_ENDED、BOMB_SCORE_CHANGED、SETTLED 等事实。每次都是完整私有快照，可丢弃低版本消息；无需按增量猜测状态。HINT 与 SNAPSHOT 本身不改变提示索引，状态版本变化后客户端重置提示。

错误格式 `{"type":"ERROR","requestId":"…","code":"STALE_VERSION"}`。主要错误：UNAUTHORIZED、INVALID_REQUEST、ROOM_REQUIRED、INVALID_ROOM_ID、ROOM_NOT_FOUND、ROOM_EXISTS、ROOM_FULL、NOT_IN_ROOM、STALE_VERSION、OLD_SEQUENCE、SEQUENCE_CONFLICT、GAME_RUNNING、GAME_NOT_STARTED、NOT_YOUR_TURN、ILLEGAL_COMBINATION、GAME_FINISHED、UNKNOWN_TYPE、SERVER_ERROR。STALE_VERSION 后先获取快照再让用户重新选择；不自动把过期出牌改成新意图。

重连先 AUTH，再按原始顺序重发未确认请求，再发新 seq 的 RECONNECT。否则新 RECONNECT 的高水位可能使未提交的旧出牌无法重试。客户端有此顺序回归测试。
