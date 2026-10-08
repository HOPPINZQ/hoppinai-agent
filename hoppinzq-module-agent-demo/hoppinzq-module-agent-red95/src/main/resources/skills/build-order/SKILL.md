---
name: build-order
description: 红警95建筑建造顺序、前置依赖关系和两步建造流程
tags: build,construction,red95
---

# 红警95 建造顺序指南

## 核心规则：两步建造流程

建造任何建筑都必须走两步，缺一不可：

```
第1步: try_buy_building_and_build(building="<建筑类型>")
       → 将建筑放入生产队列，等待就绪（最长阻塞20秒）
       → 返回 true 表示已就绪

第2步: place_building(type="BUILDING")
       → 将就绪的建筑放到地图上（位置AI自动选）
       → 只有调用这一步，建筑才真正出现
```

> **关键**：`try_buy_building_and_build` 返回 true 后，**必须**再调用 `place_building`。跳过第2步建筑不会出现！

## 建筑类型速查

| 类型代码 | 建筑名 | 功能 |
|---------|--------|------|
| POWER | 电厂 | 提供电力 |
| PROC | 矿石精炼厂 | 矿石加工，经济核心 |
| BARRACKS | 兵营 | 生产步兵 |
| HAND | 手套塔（防御） | 基础防御塔 |
| FACTORY | 战车工厂 | 生产载具 |
| SAM | 防空导弹 | 防空防御 |
| dome | 雷达圆顶 | 雷达视野 |

## 推荐开局建造顺序

```
1. deploy_mcv                    — 展开基地车
2. try_buy_building_and_build("POWER")    → place_building("BUILDING")  — 电厂
3. try_buy_building_and_build("PROC")     → place_building("BUILDING")  — 矿厂
4. try_buy_building_and_build("BARRACKS") → place_building("BUILDING")  — 兵营
5. try_buy_building_and_build("POWER")    → place_building("BUILDING")  — 第二个电厂
6. produce("E1", 5)               — 生产5个步枪兵
7. set_rally_point               — 设置兵营集结点到基地入口
```

## 前置依赖

大部分建筑需要先有电厂。建造顺序原则：
- **先电后建**：电力不足时先造电厂
- **矿厂优先**：经济是一切基础
- **防御不急**：有基础军队后再造防御塔

## 电力管理

使用 `query_player_info` 监控电力状态：
- `Power` = 当前剩余电力
- `PowerDrained` = 消耗量
- `PowerProvided` = 总供电量

**铁律**：始终保持 `Power > 0`。电力为负时所有建筑减速运转。

如果电力不足：
1. 暂停造其他建筑
2. 先 `try_buy_building_and_build("POWER")` → `place_building("BUILDING")`
3. 电力恢复后再继续

## 队列类型说明

`place_building(type="BUILDING")` 中的 type 参数是「队列类型」不是建筑名：
- `"BUILDING"` — 主建筑队列（电厂、矿厂、兵营等）
- 防御类建筑可能使用不同队列，视工具返回而定

## 生产队列管理

使用 `manage_production` 暂停/继续/取消生产队列：
- `manage_production(queueType="<队列>", action="pause")` — 暂停
- `manage_production(queueType="<队列>", action="resume")` — 继续
- `manage_production(queueType="<队列>", action="cancel")` — 取消

使用 `query_production_queue` 查看当前生产进度。
