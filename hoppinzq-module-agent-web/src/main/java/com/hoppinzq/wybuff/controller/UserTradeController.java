package com.hoppinzq.wybuff.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hoppinzq.wybuff.entity.*;
import com.hoppinzq.wybuff.mapper.BuffGoodsMapper;
import com.hoppinzq.wybuff.mapper.BuffPriceMapper;
import com.hoppinzq.wybuff.service.UserTradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trade")
public class UserTradeController {

    @Autowired
    private UserTradeService userTradeService;

    @Autowired
    private BuffPriceMapper buffPriceMapper;
    
    @Autowired
    private BuffGoodsMapper buffGoodsMapper;

    /**
     * 获取物品历史数据接口
     * 结合BuffPrice和BuffGoods，获取物品及其历史价格和数量
     */
    @GetMapping("/history/{goodsId}")
    public Map<String, Object> getGoodsHistory(@PathVariable Integer goodsId) {
        Map<String, Object> result = new HashMap<>();
        
        BuffGoods goods = buffGoodsMapper.selectById(goodsId);
        if (goods == null) {
            result.put("error", "物品不存在");
            return result;
        }
        
        LambdaQueryWrapper<BuffPrice> query = new LambdaQueryWrapper<>();
        query.eq(BuffPrice::getGoodsId, goodsId)
             .orderByDesc(BuffPrice::getCreateTime)
             .last("LIMIT 50"); // 取最近50条记录作为历史数据
             
        List<BuffPrice> historyPrices = buffPriceMapper.selectList(query);
        
        result.put("goods", goods);
        result.put("history", historyPrices);
        return result;
    }

    /**
     * 购买物品接口
     */
    @PostMapping("/buy")
    public Map<String, Object> buyGoods(@RequestParam Integer goodsId, @RequestParam Integer quantity) {
        Map<String, Object> result = new HashMap<>();
        try {
            userTradeService.buyGoods(goodsId, quantity);
            result.put("success", true);
            result.put("message", "购买成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 卖出物品接口
     */
    @PostMapping("/sell")
    public Map<String, Object> sellGoods(@RequestParam Integer goodsId, @RequestParam Integer quantity) {
        Map<String, Object> result = new HashMap<>();
        try {
            userTradeService.sellGoods(goodsId, quantity);
            result.put("success", true);
            result.put("message", "卖出成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 仓库接口
     */
    @GetMapping("/inventory")
    public List<UserInventory> getInventory() {
        return userTradeService.getInventory();
    }

    /**
     * 获取购买记录接口
     */
    @GetMapping("/purchases")
    public List<UserPurchaseRecord> getPurchases() {
        return userTradeService.getPurchaseRecords();
    }

    /**
     * 获取卖出记录接口
     */
    @GetMapping("/sells")
    public List<UserSellRecord> getSells() {
        return userTradeService.getSellRecords();
    }

    /**
     * 审计接口
     */
    @GetMapping("/audit-logs")
    public List<UserAuditLog> getAuditLogs() {
        return userTradeService.getAuditLogs();
    }

    /**
     * 获取当前账户余额
     */
    @GetMapping("/balance")
    public Map<String, Object> getBalance() {
        Map<String, Object> result = new HashMap<>();
        result.put("balance", userTradeService.getBalance());
        return result;
    }
}
