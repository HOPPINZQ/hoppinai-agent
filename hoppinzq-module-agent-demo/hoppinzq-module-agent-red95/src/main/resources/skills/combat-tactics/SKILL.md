---
name: combat-tactics
description: 红警95战斗战术、兵种运用、侦查技巧和编队管理
tags: combat,tactics,military,red95
---

# 红警95 战斗战术指南

## 侦查——胜利的前提

**看不见敌人就无法打赢。** 侦查优先级仅次于经济。

### 侦查手段
1. **移动镜头巡逻**：`camera_move_dir(direction="N", distance=10)` — 按8方向巡视地图
2. **精确移动镜头**：`camera_move_to(x=50, y=50)` — 移到指定坐标
3. **探索状态查询**：`explorer_query(x=50, y=50)` — 该坐标是否曾被探索过
4. **视野查询**：`visible_query(x=50, y=50)` — 该坐标当前是否在我方视野内
5. **未探索区域**：`get_unexplored_nearby_positions` — 获取周围未探索坐标

### 侦查战术
- 派一个便宜步兵朝四方移动探索地图边缘
- 发现敌方建筑后用 `camera_move_to` 锁定位置
- 持续监控敌方基地动态

## 兵种选择

### 步兵类
| 单位 | 特点 | 用途 |
|------|------|------|
| E1 步枪兵 | 便宜量大 | 早期防御、侦查、人海战术 |
| E3 火箭兵 | 反装甲 | 对付敌方载具 |
| SPIE 工程师 | 可占领建筑 | 占领敌方/中立建筑 |

### 载具类
| 单位 | 特点 | 用途 |
|------|------|------|
| Harvester 矿车 | 采矿 | 经济单位，需保护 |
| 轻型坦克 | 快速灵活 | 侦查骚扰、快速突击 |
| 中型坦克 | 均衡 | 主力作战单位 |
| 重型坦克 | 火力强 | 攻坚、对付建筑 |

## 移动与攻击

### 移动指令
- `move_units(actorIds, x, y, attackMove)` — 普通移动，立刻返回
- `move_units_and_wait(actorIds, x, y, maxWaitTime, toleranceDis)` — 移动并等待到达（阻塞）
- `move_units_by_direction(actorIds, direction, distance)` — 按方向移动
- `move_units_by_path(actorIds, path)` — 沿路径点移动（绕开危险区）

### 攻击指令
- `attack(attackerId, targetId)` — 命令一个单位攻击一个目标
  - 需要目标在视野内，返回 false 表示不可见/不可达
- `attackMove` 参数（在 move_units 中）：true=遇敌自动攻击，false=纯移动

### 战斗原则
1. **集中优势兵力**：不要零散进攻，攒够部队再出击
2. **先打高价值目标**：电厂 > 矿厂 > 生产建筑 > 防御塔
3. **保持火力优势**：坦克群优于步兵群
4. **及时撤退**：用 `stop_units` 取消不利战斗

## 工程师占领

使用 `occupy(occupierIds, targetIds)` 命令工程师占领建筑：
1. 先把工程师移动到目标建筑附近
2. 调用 `occupy` 执行占领
3. 可占领：敌方建筑、中立科技建筑
4. 占领敌方主基地可瞬间扭转战局

## 编队管理

使用 `form_group(actorIds, groupId)` 编组：
- `groupId` 范围 1-10
- 编组后可快速选中多组单位协同作战
- 建议：1队=主力攻击群，2队=防御群，3队=侦查群

## 防御战术

1. **基地周围放防御塔**：建造防御建筑保护核心
2. **设置集结点远离危险**：`set_rally_point` 到安全区域
3. **预留快速反应部队**：保持2-3个坦克随时支援
4. **监控敌方动向**：定期侦查敌方是否有进攻迹象

## 修复

使用 `repair_units(actorIds)` 修复受损单位：
- 修载具需要先建维修中心
- 修建筑直接调用即可
- 优先修复高价值单位（坦克 > 步兵）
