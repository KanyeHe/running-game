# MySQL / Redis

建表源文件 `server/src/main/resources/schema.sql`，persistent Profile 自动创建缺失表；当前不做已有表的破坏性迁移。

| 表 | 内容 / 唯一约束 |
|---|---|
| users | 游客 playerId、令牌、昵称；token 唯一 |
| rooms | 房间版本和完整私密 JSON 快照，id 主键 |
| matches | roomId+gameSerial 稳定对局 ID；roomId 索引 |
| player_results | 每局每人剩牌和积分，matchId+playerId 主键 |
| play_records | 完整事件，包括 PLAYED 的实体 ID/癞子身份/牌型比较值；matchId+eventNo 唯一，版本索引 |
| operations | 客户端操作序号、requestId、期望/提交房间版本、玩家和结构化组合；room+player+seq 唯一 |
| score_ledger | 结算总分，每局每人唯一 |
| bomb_ledger | 原始炸弹及冲正，trick、每家分值、reverses、组合依据；match+entry 唯一 |

rooms 快照与本次对局审计在同一个 MySQL 事务中提交，再尝试更新 Redis。SQL 的幂等唯一键与房间回执共同避免重复结算；单节点房间锁保证本进程没有双写。不能直接多副本启动，因为数据库当前没有跨进程乐观锁所有权协议。

Redis：`room:{id}` 完整快照、`room-node:{id}` 节点提示，TTL 1 天；`online:{playerId}` 在线标记 TTL 5 分钟；`session:{token}` 会话提示 TTL 1 天。当前 SQL 用户令牌不自动过期。Redis 可清空，完整恢复以 MySQL 为准。在线缓存是最近连接事件的提示，并非带心跳的可靠租约。

历史审计通过 INSERT IGNORE 的稳定主键追加；炸弹冲正通过新行引用原记录，不改写原分数记录。客户端公开视图不暴露 room 快照、令牌或其他手牌。Redis 中的私密快照同样只允许服务端访问。
