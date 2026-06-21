# hoppinzq-module-agent-22-multimodal

s22 多模态输入示例（最小独立模块）。基于 s01-baseloop 模式。

## 特性

- **MessageContent 多类型消息管道**：text / image / file
- **read_file 自动 base64**：探测 `image/*` MIME 时自动转 base64 image block
- **screenshot 工具**：通过 `Robot.createScreenCapture` 抓屏并返回 PNG base64
- **vision 适配**：根据 `AIConstants.SUPPORTS_VISION` 决定是发送图片 block 还是降级为文本描述

## 运行

```bash
mvn compile
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent22"
```

## 验证

1. 工作目录放一张 `test.png`
2. 输入 `帮我读取 test.png`
3. 若 `SUPPORTS_VISION=true`：read_file 返回 image block，agent 看到 base64 图片
4. 若 `SUPPORTS_VISION=false`（默认）：降级为文本描述 `[图片: image/png, 286KB]`
5. 输入 `截个屏` → screenshot 工具抓屏并返回 base64 PNG

## 注意

- `Robot.createScreenCapture` 在无头服务器（headless）上会抛 `AWTException`，已 try-catch 返回友好错误。
- 默认 `MODEL=deepseek-chat`，不支持 vision，故 `SUPPORTS_VISION=false`。
