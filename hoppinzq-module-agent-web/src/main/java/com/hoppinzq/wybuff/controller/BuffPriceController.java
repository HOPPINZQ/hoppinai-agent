package com.hoppinzq.wybuff.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hoppinzq.wybuff.entity.BuffPrice;
import com.hoppinzq.wybuff.service.BuffPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buff/price")
public class BuffPriceController {

    @Autowired
    private BuffPriceService service;

    @GetMapping("/page")
    public Page<BuffPrice> page(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size) {
        return service.page(new Page<>(current, size));
    }

    @GetMapping("/list")
    public List<BuffPrice> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public BuffPrice getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/goods/{goodsId}")
    public BuffPrice getByGoodsId(@PathVariable Integer goodsId) {
        return service.lambdaQuery()
                .eq(BuffPrice::getGoodsId, goodsId)
                .orderByDesc(BuffPrice::getCreateTime)
                .last("LIMIT 1")
                .one();
    }

    @GetMapping("/goods/{goodsId}/history")
    public List<BuffPrice> getHistoryByGoodsId(@PathVariable Integer goodsId) {
        return service.lambdaQuery()
                .eq(BuffPrice::getGoodsId, goodsId)
                .orderByAsc(BuffPrice::getCreateTime)
                .list();
    }

    @PostMapping("/save")
    public boolean save(@RequestBody BuffPrice entity) {
        return service.save(entity);
    }

    @PutMapping("/update")
    public boolean update(@RequestBody BuffPrice entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return service.removeById(id);
    }
}
