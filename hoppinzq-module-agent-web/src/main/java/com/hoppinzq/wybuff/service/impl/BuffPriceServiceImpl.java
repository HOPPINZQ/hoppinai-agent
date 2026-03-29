package com.hoppinzq.wybuff.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hoppinzq.wybuff.entity.BuffPrice;
import com.hoppinzq.wybuff.mapper.BuffPriceMapper;
import com.hoppinzq.wybuff.service.BuffPriceService;
import org.springframework.stereotype.Service;

@Service
public class BuffPriceServiceImpl extends ServiceImpl<BuffPriceMapper, BuffPrice> implements BuffPriceService {
}
