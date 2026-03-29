package com.hoppinzq.wybuff.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hoppinzq.wybuff.entity.BuffBillOrder;
import com.hoppinzq.wybuff.service.BuffBillOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buff/billorder")
public class BuffBillOrderController {

    @Autowired
    private BuffBillOrderService service;

    @GetMapping("/page")
    public Page<BuffBillOrder> page(@RequestParam(defaultValue = "1") Integer current, @RequestParam(defaultValue = "10") Integer size) {
        return service.page(new Page<>(current, size));
    }

    @GetMapping("/list")
    public List<BuffBillOrder> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public BuffBillOrder getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping("/save")
    public boolean save(@RequestBody BuffBillOrder entity) {
        return service.save(entity);
    }

    @PutMapping("/update")
    public boolean update(@RequestBody BuffBillOrder entity) {
        return service.updateById(entity);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return service.removeById(id);
    }
}
