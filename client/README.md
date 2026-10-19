# Cocos Creator 客户端

目标版本 3.8.8，横屏 1280×720，微信小游戏。UI 通过 Graphics/Label 程序生成，无外部素材。源码实现实际服务连接，没有静态假牌局。

## 导入与预览（尚未在当前机器执行）

1. Cocos Dashboard 导入本目录，使用 Creator 3.8.8。等待导入和脚本编译。
2. 打开 `assets/scenes/Main.scene`。源场景包含 `Game` 节点和 Bootstrap 入口。入口创建 Canvas、正交 UI Camera 和 GameTable。
3. `GameTable.ts` 的 `serverUrl` 默认 `http://localhost:8080`。手机联调须改为可访问的服务器地址；本机 localhost 不指向开发电脑。
4. 若导入器提示入口丢失，在场景根节点下创建空节点并添加 `Bootstrap` 组件，保存；请保留源码和 `.meta` 的 UUID 配对。这是待编辑器验证的兼容性恢复步骤。
5. 服务端启动后浏览器预览。四个独立浏览器配置/隐私会话分别运行，创建/加入同一个房间，四人准备。
6. `npm install`，`npm run typecheck:core`。Creator 生成 `temp/tsconfig.cocos.json` 后运行 `npm run typecheck` 验证全部引擎代码，不能用模拟 cc 类型代替真实验证。

## 操作

- 可用手牌以亮色显示，其余禁选。已选牌上移；新选择必须能扩展到至少一个服务端合法候选。
- 提示循环所有合法组合，包括同一实体集合的不同癞子解释；说明行展示替代点数。手动选中完整集合时采用候选顺序中的第一种解释，可通过提示选择其他解释。
- 出牌按钮仅在完整合法集合选定时可用。服务端重新计算校验。
- 没有“过牌”按钮。自动跳过由服务端决定，显示在桌面提示中。
- 30 秒倒计时归零后默认等待玩家，不自动过牌；超时最终策略待确认。
- 断线后指数退避重连，先重试未确认请求，再请求房间恢复。房间版本改变会重置提示和选牌。
- 结算后四人重新准备，由上一局赢家首出；每局积分单独显示。

## 微信构建（须安装编辑器与微信开发者工具）

1. 构建发布面板选择微信小游戏（`wechatgame`）。添加 Main.scene 为首场景，横屏，填写自己的真实 AppID。
2. 将服务端配置为 HTTPS/WSS；微信后台分别配置 request 和 socket 合法域名。
3. 本源码通过 `wx.request`、`wx.connectSocket` 的 SocketTask、`wx` 本地存储适配小程序；浏览器预览使用 fetch/WebSocket/localStorage。
4. 在微信开发者工具导入编辑器输出目录，执行真机调试。四台独立设备加入同房间，验证准备、发牌、出牌、提示、断网重连及结算。
5. 通过 [测试清单](../docs/test-plan.md) 后再上传体验版。当前未生成微信构建包，也未上传或发布。

非实时登录为 MVP 游客会话；尚未接入 `wx.login` 的服务端 code2Session 和真实用户绑定。

场景和 UUID 可用 `python scripts/create-scene.py` 在仓库根目录重新生成。源场景配置须由真实 Creator 导入和验证；生成 JSON 不等于构建成功。
