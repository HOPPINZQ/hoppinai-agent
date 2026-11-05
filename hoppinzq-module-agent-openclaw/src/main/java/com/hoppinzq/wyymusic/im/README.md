# 网易云私信 AI 机器人

把网易云音乐的「私信」接入 AI：定时拉取会话 → 过滤掉已经不用的会话 → 交给大模型 → 回复文字，必要时再推荐/发送一首歌。

底层调用 [NeteaseCloudMusicApi](https://hoppinzq.com:3003/docs/#/?id=neteasecloudmusicapi)。

## 流程

```
定时轮询（默认 60s）
  └─ GET /msg/private + /msg/recentcontact     拉取会话列表
      └─ 过滤：
           - 自己
           - 机器人（bot-user-ids / bot-nickname-keywords）
      └─ GET /msg/private/history?uid=xxx      拉取每个会话最近 N 条消息
          └─ 最后一条来自「对方」才处理（已对话的不管）
          └─ 超出 max-age-minutes 的旧消息不回复
          └─ 进程内按 msgId 去重，避免接口延迟导致重复回复
      └─ LLM（DeepSeek / OpenAI 兼容协议）
          └─ 输出 JSON：reply + songQuery + songReason
      └─ GET /cloudsearch?type=1               按 songQuery 搜索歌曲
      └─ GET /send/text                        发送回复
      └─ GET /send/song                        发送歌曲卡片（可选）
```

## 配置

```yaml
wyy:
  im:
    enabled: true
    base-url: https://hoppinzq.com:3003/
    cookie: ${WYY_COOKIE:}          # 必填，建议用环境变量注入
    poll-interval-ms: 60000
    dry-run: false                  # 先设 true 演练，确认回复内容再关闭
    send-song: true
    max-age-minutes: 180
    bot-user-ids: [9003]            # 云音乐小秘书
    bot-nickname-keywords: [小秘书, 机器人, 客服, 官方, 助手, bot]
```

启动前设置：

```bash
export WYY_COOKIE='MUSIC_U=xxxx; __csrf=xxxx'
export DEEPSEEK_API_KEY='sk-xxxx'
```

> Cookie 可以通过浏览器登录 music.163.com 后从开发者工具复制，也可以复用
> `WyyService#loginQrKey/loginQrCreate/loginQrCheck` 的扫码登录结果。
> **不要把 Cookie 提交到仓库。**

## 调试接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/wyy/im/status` | 查看开关、Cookie 是否配置、当前账号 uid |
| POST | `/wyy/im/run` | 立即执行一轮（会真实发私信，dry-run 时只打印计划） |
| GET | `/wyy/im/conversations` | 查看会话列表（只读） |
| GET | `/wyy/im/history?uid=123` | 查看与某人的聊天记录（只读） |

建议第一次先配置 `dry-run: true`，调用 `POST /wyy/im/run` 看 AI 生成的回复，确认无误后再改为 `false`。

## 注册给主 Agent 的工具

这些能力同时注册到了主对话 Agent（`/agent/runTask`、`/agent/streamChat`），由 `AgentChatController`
在启动时通过 `WyyImTools.create(...)` 加入工具列表，系统提示词里会自动出现：

| 工具 | 说明 |
| --- | --- |
| `wyy_im_conversations` | 查看私信会话列表（对方 uid、昵称、未读数、最后消息） |
| `wyy_im_history` | 查看与某个 uid 的聊天记录 |
| `wyy_search_song` | 搜索歌曲，返回歌曲 id / 歌名 / 歌手 |
| `wyy_send_text` | 以当前账号发送文本私信 |
| `wyy_send_song` | 以当前账号发送歌曲私信（可传 songId 或先搜歌） |
| `wyy_im_auto_reply` | 立即执行一轮完整的自动回复 |

`wyy_send_text` / `wyy_send_song` 会检查 `wyy.im.dry-run`，演练模式下只返回拟发送内容，不会真正发送。

## AI 输出约定

系统提示词要求模型只输出：

```json
{"reply":"回复正文","songQuery":"周杰伦 晴天","songReason":"今天适合听点轻快的"}
```

- `songQuery` 为 `null` 时只发文字；
- 非空时用 `/cloudsearch` 取第一首结果，把歌曲卡片发给对方；
- 模型输出不是合法 JSON 时，整段文本会作为回复内容兜底。

## 注意

- NeteaseCloudMusicApi 对相同 URL 有约 2 分钟缓存，因此所有私信接口都带了 `timestamp` 参数。
- 未登录 / Cookie 失效时接口会返回 301 或业务错误，日志中会输出 `无法解析当前登录账号 uid`。
- 发送私信属于真实外部操作，请遵守平台规则，控制频率。