package com.hoppinzq.wybuff.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hoppinzq.wybuff.entity.BuffGoods;
import com.hoppinzq.wybuff.entity.BuffPrice;
import com.hoppinzq.wybuff.service.BuffGoodsService;
import com.hoppinzq.wybuff.service.BuffPriceService;
import com.hoppinzq.wybuff.task.BuffDataSyncTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/buff/goods")
public class BuffGoodsController {

    @Autowired
    private BuffGoodsService service;

    @Autowired
    private BuffPriceService priceService;

    @Autowired
    private BuffDataSyncTask syncTask;

    private void attachPrice(BuffGoods goods) {
        if (goods != null) {
            BuffPrice price = priceService.lambdaQuery()
                    .eq(BuffPrice::getGoodsId, goods.getId())
                    .orderByDesc(BuffPrice::getCreateTime)
                    .last("LIMIT 1")
                    .one();
            if (price != null) {
                goods.setSellMinPrice(price.getSellMinPrice());
                goods.setSellReferencePrice(price.getSellReferencePrice());
                // goods.setSteamPrice(price.getSteamPrice()); // Note: BuffPrice doesn't seem to have steamPrice, just steamPriceCny
                goods.setSteamPriceCny(price.getSteamPriceCny());
            }
        }
    }

    private void attachPrices(List<BuffGoods> list) {
        if (list != null) {
            for (BuffGoods goods : list) {
                attachPrice(goods);
            }
        }
    }

    @GetMapping("/page")
    public Page<BuffGoods> page(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size) {
        Page<BuffGoods> pageResult = service.page(new Page<>(current, size));
        attachPrices(pageResult.getRecords());
        return pageResult;
    }

    @GetMapping("/list")
    public List<BuffGoods> list() {
        List<BuffGoods> list = service.list();
        attachPrices(list);
        return list;
    }

    @GetMapping("/{id}")
    public BuffGoods getById(@PathVariable Long id) {
        BuffGoods goods = service.getById(id);
        attachPrice(goods);
        return goods;
    }

    @GetMapping("/goods/{goodsId}")
    public BuffGoods getByGoodsId(@PathVariable Integer goodsId) {
        BuffGoods goods = service.lambdaQuery()
                .eq(BuffGoods::getId, goodsId)
                .one();
        attachPrice(goods);
        return goods;
    }

    @GetMapping("/search")
    public Page<BuffGoods> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        LambdaQueryWrapper<BuffGoods> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.and(w -> w.like(BuffGoods::getName, name)
                    .or()
                    .like(BuffGoods::getMarketHashName, name));
        }
        wrapper.orderByDesc(BuffGoods::getUpdateTime);
        Page<BuffGoods> pageResult = service.page(new Page<>(current, size), wrapper);
        attachPrices(pageResult.getRecords());
        return pageResult;
    }

    @PostMapping("/save")
    public boolean save(@RequestBody BuffGoods entity) {
        return service.save(entity);
    }

    @PutMapping("/update")
    public boolean update(@RequestBody BuffGoods entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return service.removeById(id);
    }

    @PostMapping("/sync/goods")
    public ResponseEntity<String> syncGoods() {
        CompletableFuture.runAsync(() -> syncTask.syncBuffData());
        return ResponseEntity.ok("同步任务已启动");
    }

    @PostMapping("/sync/price-history")
    public ResponseEntity<String> syncPriceHistory() {
        CompletableFuture.runAsync(() -> syncTask.syncAllPriceHistory());
        return ResponseEntity.ok("价格历史同步任务已启动");
    }
}
