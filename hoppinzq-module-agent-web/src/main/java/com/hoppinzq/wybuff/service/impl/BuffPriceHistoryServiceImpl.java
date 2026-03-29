package com.hoppinzq.wybuff.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hoppinzq.wybuff.entity.BuffPriceHistory;
import com.hoppinzq.wybuff.mapper.BuffPriceHistoryMapper;
import com.hoppinzq.wybuff.service.BuffPriceHistoryService;
import org.springframework.stereotype.Service;

@Service
public class BuffPriceHistoryServiceImpl extends ServiceImpl<BuffPriceHistoryMapper, BuffPriceHistory> implements BuffPriceHistoryService {
}
