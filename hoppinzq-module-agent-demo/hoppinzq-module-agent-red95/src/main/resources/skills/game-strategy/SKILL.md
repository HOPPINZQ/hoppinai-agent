---
name: game-strategy
description: 红警95总体战略规划，包含开局/发展/进攻三阶段策略和胜利判断
tags: strategy,game-plan,red95
---

# 红警95 总体战略规划

## 三阶段战略

### 阶段一：开局（0-3分钟）
目标：建立经济基础和基本防御

1. **部署基地**：`deploy_mcv`（开局第一件事，一局只调一次）
2. **建造电厂**：`try_buy_building_and_build("POWER")` → `place_building("BUILDING")`
3. **建造矿厂**：`try_buy_building_and_build("PROC")` → `place_building("BUILDING")`
   - 矿厂提供矿石收入，是经济的核心
4. **建造兵营**：`try_buy_building_and_build("BARRACKS")` → `place_building("BUILDING")`
5. **生产基础步兵**：`produce("E1", 3)` — 步枪兵用于早期防御和侦查
6. **设置集结点**：`set_rally_point` 到基地入口附近

> 注意：每次建造都要走两步——先 `try_buy_building_and_build`（放进队列等就绪），再 `place_building`（落成到地图）。

### 阶段二：发展（3-10分钟）
目标：扩展经济、攀升科技、组建军队

1. **扩展矿区**：再造1-2个矿厂增加收入
2. **建造车间**：解锁载具生产
3. **生产矿车**：确保至少2辆矿车在采矿
4. **建造防御塔**：在基地周围放置防御（`try_buy_building_and_build` 防御类建筑）
5. **攀升科技**：建造科技中心解锁高级兵种
6. **组建军队**：批量生产主力作战单位
7. **侦查地图**：用 `camera_move_dir` 巡逻未探索区域

### 阶段三：进攻（10分钟+）
目标：摧毁敌方所有建筑和单位

1. **侦查敌方基地**：用 `visible_units(type="enemy", range="all")` 或移动侦查单位
2. **选择突破口**：找到敌方防御薄弱的方向
3. **集中攻击**：
   - 先打电厂 → 瘫痪敌方电力
   - 再打生产建筑 → 阻止敌方补充兵力
   - 最后打主基地 → 彻底摧毁
4. **持续施压**：不断生产单位送往前线，保持攻势

## 胜利判断

通过以下方式判断是否胜利：
1. 调用 `get_game_state`，查看是否还有敌方单位
2. 调用 `query_player_info`，检查敌方资源/建筑状态
3. 如果 `visible_units(type="enemy", range="all")` 返回空或极少量单位，说明即将胜利
4. 当确认敌方主基地已摧毁、无可战斗单位时，宣布胜利

## 失败恢复

如果遭受攻击或经济崩溃：
1. 立即 `get_game_state` 评估损失
2. 优先重建电厂恢复电力
3. 用现有单位进行防御
4. 经济恢复后再反攻
