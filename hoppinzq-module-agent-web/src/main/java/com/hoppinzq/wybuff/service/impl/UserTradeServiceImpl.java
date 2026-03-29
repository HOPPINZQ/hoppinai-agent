package com.hoppinzq.wybuff.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hoppinzq.wybuff.entity.*;
import com.hoppinzq.wybuff.mapper.*;
import com.hoppinzq.wybuff.service.UserTradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class UserTradeServiceImpl implements UserTradeService {

    @Autowired
    private UserPurchaseRecordMapper purchaseRecordMapper;
    
    @Autowired
    private UserSellRecordMapper sellRecordMapper;
    
    @Autowired
    private UserInventoryMapper inventoryMapper;
    
    @Autowired
    private UserAuditLogMapper auditLogMapper;
    
    @Autowired
    private BuffPriceMapper buffPriceMapper;
    
    @Autowired
    private UserAccountMapper userAccountMapper;

    private UserAccount getUserAccount() {
        UserAccount account = userAccountMapper.selectById(1L);
        if (account == null) {
            account = new UserAccount();
            account.setId(1L);
            account.setBalance(new BigDecimal("100000.00"));
            userAccountMapper.insert(account);
        }
        return account;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void buyGoods(Integer goodsId, Integer quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("购买数量必须大于0");
        }

        // 获取最近一次价格
        LambdaQueryWrapper<BuffPrice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BuffPrice::getGoodsId, goodsId)
                .orderByDesc(BuffPrice::getCreateTime)
                .last("LIMIT 1");
        BuffPrice latestPrice = buffPriceMapper.selectOne(queryWrapper);
        
        if (latestPrice == null || latestPrice.getSellMinPrice() == null) {
            throw new RuntimeException("未找到物品的有效价格信息");
        }

        BigDecimal price = latestPrice.getSellMinPrice();
        BigDecimal totalCost = price.multiply(new BigDecimal(quantity));

        // 检查并扣除余额
        UserAccount account = getUserAccount();
        if (account.getBalance().compareTo(totalCost) < 0) {
            throw new RuntimeException("余额不足，当前余额：" + account.getBalance() + "，需要：" + totalCost);
        }
        account.setBalance(account.getBalance().subtract(totalCost));
        account.setUpdateTime(LocalDateTime.now());
        userAccountMapper.updateById(account);

        // 1. 记录购买记录
        UserPurchaseRecord purchase = new UserPurchaseRecord();
        purchase.setGoodsId(goodsId);
        purchase.setBuyPrice(price);
        purchase.setQuantity(quantity);
        purchase.setRemainQuantity(quantity);
        purchase.setCreateTime(LocalDateTime.now());
        purchaseRecordMapper.insert(purchase);

        // 2. 更新仓库
        LambdaQueryWrapper<UserInventory> invQuery = new LambdaQueryWrapper<>();
        invQuery.eq(UserInventory::getGoodsId, goodsId);
        UserInventory inventory = inventoryMapper.selectOne(invQuery);
        
        if (inventory == null) {
            inventory = new UserInventory();
            inventory.setGoodsId(goodsId);
            inventory.setQuantity(quantity);
            inventory.setCreateTime(LocalDateTime.now());
            inventory.setUpdateTime(LocalDateTime.now());
            inventoryMapper.insert(inventory);
        } else {
            inventory.setQuantity(inventory.getQuantity() + quantity);
            inventory.setUpdateTime(LocalDateTime.now());
            inventoryMapper.updateById(inventory);
        }

        // 3. 记录审计日志
        UserAuditLog audit = new UserAuditLog();
        audit.setActionType("BUY");
        audit.setGoodsId(goodsId);
        audit.setQuantity(quantity);
        audit.setPrice(price);
        audit.setDescription("购买物品, 单价: " + price + ", 数量: " + quantity);
        audit.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(audit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sellGoods(Integer goodsId, Integer quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("卖出数量必须大于0");
        }

        // 获取最近一次价格作为卖出价
        LambdaQueryWrapper<BuffPrice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BuffPrice::getGoodsId, goodsId)
                .orderByDesc(BuffPrice::getCreateTime)
                .last("LIMIT 1");
        BuffPrice latestPrice = buffPriceMapper.selectOne(queryWrapper);
        
        if (latestPrice == null || latestPrice.getSellMinPrice() == null) {
            throw new RuntimeException("未找到物品的有效卖出价格信息");
        }
        
        BigDecimal sellPrice = latestPrice.getSellMinPrice();

        // 检查仓库库存
        LambdaQueryWrapper<UserInventory> invQuery = new LambdaQueryWrapper<>();
        invQuery.eq(UserInventory::getGoodsId, goodsId);
        UserInventory inventory = inventoryMapper.selectOne(invQuery);
        
        if (inventory == null || inventory.getQuantity() < quantity) {
            throw new RuntimeException("库存不足");
        }

        // 扣减仓库
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setUpdateTime(LocalDateTime.now());
        inventoryMapper.updateById(inventory);

        // 先进先出扣减购买记录，并生成卖出记录
        int remainToSell = quantity;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalRevenue = sellPrice.multiply(new BigDecimal(quantity));

        LambdaQueryWrapper<UserPurchaseRecord> purchaseQuery = new LambdaQueryWrapper<>();
        purchaseQuery.eq(UserPurchaseRecord::getGoodsId, goodsId)
                .gt(UserPurchaseRecord::getRemainQuantity, 0)
                .orderByAsc(UserPurchaseRecord::getCreateTime);
        
        List<UserPurchaseRecord> purchases = purchaseRecordMapper.selectList(purchaseQuery);
        
        for (UserPurchaseRecord purchase : purchases) {
            if (remainToSell <= 0) break;
            
            int deductQty = Math.min(purchase.getRemainQuantity(), remainToSell);
            
            // 更新购买记录剩余数量
            purchase.setRemainQuantity(purchase.getRemainQuantity() - deductQty);
            purchaseRecordMapper.updateById(purchase);
            
            // 增加卖出记录
            UserSellRecord sellRecord = new UserSellRecord();
            sellRecord.setGoodsId(goodsId);
            sellRecord.setPurchaseId(purchase.getId());
            sellRecord.setBuyPrice(purchase.getBuyPrice());
            sellRecord.setSellPrice(sellPrice);
            sellRecord.setQuantity(deductQty);
            
            // 计算利润
            BigDecimal buyTotal = purchase.getBuyPrice().multiply(new BigDecimal(deductQty));
            BigDecimal sellTotal = sellPrice.multiply(new BigDecimal(deductQty));
            BigDecimal profit = sellTotal.subtract(buyTotal);
            sellRecord.setProfit(profit);
            sellRecord.setCreateTime(LocalDateTime.now());
            
            sellRecordMapper.insert(sellRecord);
            
            totalProfit = totalProfit.add(profit);
            remainToSell -= deductQty;
        }

        if (remainToSell > 0) {
            throw new RuntimeException("扣减购买记录时出现数据不一致，库存足够但购买记录不足");
        }

        // 增加余额
        UserAccount account = getUserAccount();
        account.setBalance(account.getBalance().add(totalRevenue));
        account.setUpdateTime(LocalDateTime.now());
        userAccountMapper.updateById(account);

        // 记录审计日志
        UserAuditLog audit = new UserAuditLog();
        audit.setActionType("SELL");
        audit.setGoodsId(goodsId);
        audit.setQuantity(quantity);
        audit.setPrice(sellPrice);
        audit.setDescription("卖出物品, 单价: " + sellPrice + ", 数量: " + quantity);
        audit.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(audit);
    }

    @Override
    public List<UserInventory> getInventory() {
        return inventoryMapper.selectList(null);
    }

    @Override
    public List<UserPurchaseRecord> getPurchaseRecords() {
        LambdaQueryWrapper<UserPurchaseRecord> query = new LambdaQueryWrapper<>();
        query.orderByDesc(UserPurchaseRecord::getCreateTime);
        return purchaseRecordMapper.selectList(query);
    }

    @Override
    public List<UserSellRecord> getSellRecords() {
        LambdaQueryWrapper<UserSellRecord> query = new LambdaQueryWrapper<>();
        query.orderByDesc(UserSellRecord::getCreateTime);
        return sellRecordMapper.selectList(query);
    }

    @Override
    public List<UserAuditLog> getAuditLogs() {
        LambdaQueryWrapper<UserAuditLog> query = new LambdaQueryWrapper<>();
        query.orderByDesc(UserAuditLog::getCreateTime);
        return auditLogMapper.selectList(query);
    }

    @Override
    public BigDecimal getBalance() {
        return getUserAccount().getBalance();
    }
}
