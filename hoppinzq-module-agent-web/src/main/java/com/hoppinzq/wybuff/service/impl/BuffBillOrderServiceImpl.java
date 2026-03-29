package com.hoppinzq.wybuff.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hoppinzq.wybuff.entity.BuffBillOrder;
import com.hoppinzq.wybuff.mapper.BuffBillOrderMapper;
import com.hoppinzq.wybuff.service.BuffBillOrderService;
import org.springframework.stereotype.Service;

@Service
public class BuffBillOrderServiceImpl extends ServiceImpl<BuffBillOrderMapper, BuffBillOrder> implements BuffBillOrderService {
}
