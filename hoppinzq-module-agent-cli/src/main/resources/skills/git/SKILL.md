---
name: git
description: Git工作流助手和最佳实践
tags: git,vcs,workflow
---

# Git工作流技能

## 目标
指导用户完成Git工作流程和版本控制的最佳实践。

## 分支策略

### 功能分支工作流
1. 从`main`或`develop`创建功能分支
2. 提交清晰、描述性的消息
3. 推送分支并创建拉取请求
4. 审查并处理反馈
5. 合并拉取请求
6. 删除功能分支

### 分支命名
- `feature/描述` - 新功能
- `bugfix/描述` - 错误修复
- `hotfix/描述` - 紧急生产修复
- `refactor/描述` - 代码重构

## 提交消息

### 格式
```
<类型>(<范围>): <主题>

<正文>

<页脚>
```

### 类型
- `feat`：新功能
- `fix`：错误修复
- `docs`：文档变更
- `style`：代码风格变更（格式化等）
- `refactor`：代码重构
- `test`：添加或更新测试
- `chore`：构建过程或辅助工具变更

### 示例
```
feat(auth): 添加OAuth2登录支持

- 实现OAuth2身份验证流程
- 添加令牌刷新机制
- 更新登录界面

关闭 #123
```

```
fix(api): 处理服务器的空响应

当服务器返回空响应时，客户端现在显示错误消息而不是崩溃。

修复 #456
```

## 常用命令

### 分支操作
```bash
# 创建并切换到新分支
git checkout -b feature/my-feature

# 列出所有分支
git branch -a

# 删除本地分支
git branch -d feature/my-feature

# 删除远程分支
git push origin --delete feature/my-feature
```

### 暂存和提交
```bash
# 暂存特定文件
git add path/to/file

# 暂存所有更改
git add .

# 提交并附带消息
git commit -m "feat: 添加新功能"

# 修改最后一次提交（谨慎使用！）
git commit --amend

# 一步完成暂存和提交
git commit -am "fix: 拼写错误"
```

### 推送和拉取
```bash
# 推送当前分支
git push

# 推送分支到远程并建立跟踪
git push -u origin feature/my-feature

# 拉取最新更改
git pull

# 使用rebase方式拉取
git pull --rebase
```

## 最佳实践

### 应该做
- 编写清晰、描述性的提交消息
- 保持提交专注于一个变更
- 推送前先拉取以避免冲突
- 提交前审查更改
- 使用`.gitignore`排除生成的文件

### 不应该做
- 提交敏感数据（API密钥、密码）
- 提交大型二进制文件
- 进行过大或不专注的提交
- 对共享分支进行强制推送
- 忽略合并冲突

## 故障排除

### 撤销更改
```bash
# 取消文件暂存
git reset HEAD path/to/file

# 丢弃本地更改
git checkout -- path/to/file

# 撤销最后一次提交（保留更改）
git reset --soft HEAD~1

# 撤销最后一次提交（丢弃更改）
git reset --hard HEAD~1
```

### 解决合并冲突
1. 识别冲突文件：`git status`
2. 编辑文件以解决冲突
3. 将冲突标记为已解决：`git add path/to/file`
4. 完成合并：`git commit`

## 提示
- 开始新工作前始终先拉取
- 使用命令式语气编写提交消息
- 所有工作都使用分支
- 保持工作目录干净
- 提交前审查差异
