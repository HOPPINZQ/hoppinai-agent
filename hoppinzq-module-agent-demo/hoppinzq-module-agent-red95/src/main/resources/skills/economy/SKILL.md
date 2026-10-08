---
name: economy
description: 红警95经济管理、矿车调度、资源分配和电力平衡
tags: economy,resource,management,red95
---

# 红警95 经济管理指南

## 经济是战争的基础

没有钱就没有兵，没有兵就没有胜利。经济管理优先级永远最高。

## 资金监控

使用 `query_player_info` 或 `get_game_state` 查看：
- **金钱**：当前可用资金
- **电力**：Power/PowerDrained/PowerProvided

**健康经济指标**：
- 金钱持续增长（不为零）
- 电力 Power > 0（始终为正）
- 至少有2个矿厂在运作

## 矿车管理

矿车（Harvester）是经济命脉：

1. **确保矿车数量**：每个矿厂至少配1辆矿车
2. **保护矿车**：矿车被攻击时及时用军队保护或调回基地
3. **监控矿车状态**：用 `visible_units(type="friendly", range="all")` 查看矿车位置
4. **矿车生产**：`try_buy_produce_unit(unitName="HARV")` → `produce(unitType="HARV", quantity=1)`

## 资源分配优先级

当资金有限时，按以下顺序分配：

| 优先级 | 投资方向 | 理由 |
|-------|---------|------|
| P0 | 修复电力（造电厂） | 没电一切停摆 |
| P0 | 保护矿车 | 没钱就无法继续 |
| P1 | 造矿厂 | 增加收入 |
| P1 | 造矿车 | 确保采矿效率 |
| P2 | 造兵营/车间 | 解锁兵种 |
| P3 | 造基础防御 | 防止被偷袭 |
| P4 | 生产军队 | 进攻/防御 |
| P5 | 科技升级 | 攀升高级兵种 |

## 电力管理

### 电力不足的征兆
- 建筑/生产变慢
- `query_player_info` 显示 Power ≤ 0

### 电力管理策略
1. **提前规划**：每造2-3个建筑就补一个电厂
2. **监控频率**：每隔3-5回合查一次 `query_player_info`
3. **优先修复**：电力不足时立即停止一切非必要建造，先造电厂

## 生产队列优化

### 批量生产
- `produce(unitType="E1", quantity=10)` — 一次造10个，比调10次高效
- 但注意资金够不够

### 队列管理
- `query_production_queue(queueType)` — 查看队列状态
- `manage_production(queueType, action)` — 暂停/继续/取消
- 资金紧张时可以暂停次要队列，集中资源到关键单位

### 集结点
- 每个生产建筑都要 `set_rally_point` 设置集结点
- 集结点设在基地入口或前线，方便集中兵力

## 经济崩溃恢复

如果金钱归零或矿车全灭：
1. 立即 `get_game_state` 评估局势
2. 确保至少有1个矿厂和1辆矿车在运作
3. 如果矿车全灭：优先 `produce` 新矿车
4. 如果矿厂全毁：优先 `try_buy_building_and_build("PROC")`
5. 停止一切军事生产，全力恢复经济
6. 经济恢复后再重建军队
