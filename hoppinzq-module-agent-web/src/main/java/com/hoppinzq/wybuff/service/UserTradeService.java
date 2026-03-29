package com.hoppinzq.wybuff.service;

import com.hoppinzq.wybuff.entity.UserAuditLog;
import com.hoppinzq.wybuff.entity.UserInventory;
import com.hoppinzq.wybuff.entity.UserPurchaseRecord;
import com.hoppinzq.wybuff.entity.UserSellRecord;

import java.util.List;

public interface UserTradeService {
    void buyGoods(Integer goodsId, Integer quantity);
    void sellGoods(Integer goodsId, Integer quantity);
    List<UserInventory> getInventory();
    List<UserPurchaseRecord> getPurchaseRecords();
    List<UserSellRecord> getSellRecords();
    List<UserAuditLog> getAuditLogs();
    java.math.BigDecimal getBalance();
}
