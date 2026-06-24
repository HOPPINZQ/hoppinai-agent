package com.hoppinzq.csgo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hoppinzq.csgo.client.CsqaqInfoClient;
import com.hoppinzq.csgo.dto.CsqaqGoodsResponse;
import com.hoppinzq.csgo.entity.CsgoGoods;
import com.hoppinzq.csgo.mapper.CsgoGoodsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CSGO饰品基本信息服务
 */
@Slf4j
@Service
public class CsgoGoodsService {

    private static final int PAGE_SIZE = 500;

    private final CsgoGoodsMapper csgoGoodsMapper;
    private final CsqaqInfoClient csqaqInfoClient;

    public CsgoGoodsService(CsgoGoodsMapper csgoGoodsMapper, CsqaqInfoClient csqaqInfoClient) {
        this.csgoGoodsMapper = csgoGoodsMapper;
        this.csqaqInfoClient = csqaqInfoClient;
    }

    /**
     * 初始化全量饰品数据：分页拉取直到无数据
     */
    public String initAllGoods() {
        int totalCount = 0;
        int pageIndex = 1;
        while (true) {
            CsqaqGoodsResponse response = csqaqInfoClient.fetchPage(pageIndex, PAGE_SIZE);

            if (response == null || response.getData() == null || response.getData().getData() == null) {
                log.warn("第{}页返回数据为空, 停止", pageIndex);
                break;
            }

            List<CsqaqGoodsResponse.CsqaqGoodsItem> items = response.getData().getData();
            if (items.isEmpty()) {
                log.info("第{}页无数据, 拉取完毕", pageIndex);
                break;
            }

            int saved = saveItems(items);
            totalCount += saved;
            log.info("第{}页完成, 保存/更新{}条", pageIndex, saved);
            pageIndex++;
        }
        String msg = "初始化完成, 共处理" + totalCount + "条饰品记录";
        log.info("===== {} =====", msg);
        return msg;
    }

    /**
     * 轮询拉取一页饰品数据并保存
     */
    public CsqaqGoodsResponse pollPage(int pageIndex) {
        CsqaqGoodsResponse response = csqaqInfoClient.fetchPage(pageIndex, PAGE_SIZE);

        if (response == null || response.getData() == null || response.getData().getData() == null) {
            log.warn("API返回数据为空, pageIndex={}", pageIndex);
            return response;
        }

        List<CsqaqGoodsResponse.CsqaqGoodsItem> items = response.getData().getData();
        if (items.isEmpty()) {
            log.info("第{}页无数据", pageIndex);
            return response;
        }

        int saved = saveItems(items);
        log.info("第{}页处理完成, 保存/更新{}条", pageIndex, saved);
        return response;
    }

    /**
     * 获取数据库中饰品总数
     */
    public long getTotalCount() {
        return csgoGoodsMapper.selectCount(null);
    }

    /**
     * 按名称搜索饰品
     */
    public List<CsgoGoods> searchGoods(String keyword) {
        LambdaQueryWrapper<CsgoGoods> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(CsgoGoods::getName, keyword)
                .last("LIMIT 50");
        return csgoGoodsMapper.selectList(wrapper);
    }

    private int saveItems(List<CsqaqGoodsResponse.CsqaqGoodsItem> items) {
        int count = 0;
        for (CsqaqGoodsResponse.CsqaqGoodsItem item : items) {
            CsgoGoods entity = convertToEntity(item);
            CsgoGoods existing = csgoGoodsMapper.selectById(item.getId());
            if (existing != null) {
                entity.setCreateTime(existing.getCreateTime());
                csgoGoodsMapper.updateById(entity);
            } else {
                csgoGoodsMapper.insert(entity);
            }
            count++;
        }
        return count;
    }

    private CsgoGoods convertToEntity(CsqaqGoodsResponse.CsqaqGoodsItem item) {
        CsgoGoods entity = new CsgoGoods();
        entity.setGoodsId(item.getId());
        entity.setName(item.getName());
        entity.setExteriorLocalizedName(item.getExteriorLocalizedName());
        entity.setRarityLocalizedName(item.getRarityLocalizedName());
        entity.setImg(item.getImg());
        return entity;
    }
}
