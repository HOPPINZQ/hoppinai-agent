# hoppinzq-module-agent-23-sandbox

s23 安全沙箱示例（最小独立模块）。基于 s01-baseloop 模式。

## 四层防护

bash 命令执行时按以下顺序逐层拦截：

| 层级 | 类 | 启用条件 | 作用 |
|------|-----|---------|------|
| L1 | `StaticAnalyzer` | 总是 | 黑名单 regex 拦截高危命令（rm -rf /、mkfs、fork bomb、curl|sh、dd if=、shutdown...） |
| L2 | `DirectoryJail` | 总是 | 工作目录监禁，拒绝 `..` / 绝对路径越界 |
| L3 | `OsSandbox` | 仅 Linux，需 `bwrap` | `bwrap --unshare-all` 包裹执行 |
| L4 | `ContainerIsolation` | 需 docker daemon | `docker run --network=none --read-only` 隔离 |
| 降级 | `SandboxRunner.runWithTimeout` | — | 带超时的 ProcessBuilder |

## 运行

```bash
mvn compile
mvn exec:java -Dexec.mainClass="com.hoppinzq.agent.Agent23"
```

## 验证

1. 让 agent 执行 `rm -rf /tmp/test` → L1 拦截
2. 让 agent 执行 `ls ../../etc` → L2 拦截
3. 让 agent 执行 `echo hello` → 通过 L1+L2，降级到带超时 ProcessBuilder
4. Linux 环境下：L3 bwrap 包装（需 `apt install bubblewrap`）
5. 有 docker 环境下：L4 容器隔离（手动开启 `enableContainer`）

## 配置

`SandboxRunner` 通过 builder 控制层级：

```java
SandboxRunner sandbox = new SandboxRunner.Builder()
        .workDir(Path.of("."))
        .enableOsSandbox(System.getProperty("os.name").toLowerCase().contains("linux"))
        .enableContainer(false)  // 默认关闭，手动开启
        .build();
```
