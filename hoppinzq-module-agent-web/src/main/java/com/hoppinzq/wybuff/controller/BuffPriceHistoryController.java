package com.hoppinzq.wybuff.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hoppinzq.wybuff.entity.BuffPriceHistory;
import com.hoppinzq.wybuff.service.BuffPriceHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buff/pricehistory")
public class BuffPriceHistoryController {

    @Autowired
    private BuffPriceHistoryService service;

    @GetMapping("/page")
    public Page<BuffPriceHistory> page(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size) {
        return service.page(new Page<>(current, size));
    }

    @GetMapping("/list")
    public List<BuffPriceHistory> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public BuffPriceHistory getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/goods/{goodsId}")
    public List<BuffPriceHistory> getByGoodsId(
            @PathVariable Integer goodsId,
            @RequestParam(required = false) String priceType) {
        if (priceType != null && !priceType.isEmpty()) {
            return service.lambdaQuery()
                    .eq(BuffPriceHistory::getGoodsId, goodsId)
                    .eq(BuffPriceHistory::getPriceType, priceType)
                    .orderByAsc(BuffPriceHistory::getTimestamp)
                    .list();
        }
        return service.lambdaQuery()
                .eq(BuffPriceHistory::getGoodsId, goodsId)
                .orderByAsc(BuffPriceHistory::getTimestamp)
                .list();
    }

    @GetMapping("/goods/{goodsId}/price-types")
    public List<String> getPriceTypesByGoodsId(@PathVariable Integer goodsId) {
        return service.lambdaQuery()
                .eq(BuffPriceHistory::getGoodsId, goodsId)
                .select(BuffPriceHistory::getPriceType)
                .list()
                .stream()
                .map(BuffPriceHistory::getPriceType)
                .distinct()
                .toList();
    }

    @PostMapping("/save")
    public boolean save(@RequestBody BuffPriceHistory entity) {
        return service.save(entity);
    }

    @PutMapping("/update")
    public boolean update(@RequestBody BuffPriceHistory entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return service.removeById(id);
    }
}
