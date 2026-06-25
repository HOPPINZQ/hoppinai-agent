package com.hoppinzq.csgo.service;

import com.hoppinzq.csgo.client.CsqaqInfoClient;
import com.hoppinzq.csgo.dto.CsqaqGoodsDetailResponse;
import com.hoppinzq.csgo.entity.CsgoGoods;
import com.hoppinzq.csgo.entity.CsgoMarketPrice;
import com.hoppinzq.csgo.mapper.CsgoGoodsMapper;
import com.hoppinzq.csgo.mapper.CsgoMarketPriceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CSGO饰品市场价格服务 (api2: 单件饰品详情)
 */
@Slf4j
@Service
public class CsgoMarketPriceService {

    private static final int PAGE_SIZE = 500;

    private final CsgoMarketPriceMapper csgoMarketPriceMapper;
    private final CsgoGoodsMapper csgoGoodsMapper;
    private final CsqaqInfoClient csqaqInfoClient;

    public CsgoMarketPriceService(CsgoMarketPriceMapper csgoMarketPriceMapper,
                                  CsgoGoodsMapper csgoGoodsMapper,
                                  CsqaqInfoClient csqaqInfoClient) {
        this.csgoMarketPriceMapper = csgoMarketPriceMapper;
        this.csgoGoodsMapper = csgoGoodsMapper;
        this.csqaqInfoClient = csqaqInfoClient;
    }

    /**
     * 拉取单个饰品详情并保存
     */
    public CsqaqGoodsDetailResponse fetchAndSaveDetail(int goodsId) {
        CsqaqGoodsDetailResponse response = csqaqInfoClient.fetchGoodsDetail(goodsId);

        if (response == null || response.getData() == null || response.getData().getGoodsInfo() == null) {
            log.warn("饰品详情返回为空, goodsId={}", goodsId);
            return response;
        }

        CsqaqGoodsDetailResponse.GoodsInfo info = response.getData().getGoodsInfo();

        // 保存/更新市场价格
        CsgoMarketPrice price = convertToPriceEntity(info);
        CsgoMarketPrice existing = csgoMarketPriceMapper.selectById(goodsId);
        if (existing != null) {
            price.setCreateTime(existing.getCreateTime());
            csgoMarketPriceMapper.updateById(price);
            log.info("更新市场价格: goodsId={}", goodsId);
        } else {
            csgoMarketPriceMapper.insert(price);
            log.info("新增市场价格: goodsId={}", goodsId);
        }

        // 同时更新csgo_goods基本信息（api2返回更完整的字段）
        updateGoodsInfo(info);

        return response;
    }

    /**
     * 批量初始化所有饰品的市场价格：遍历csgo_goods表，逐个拉取详情
     */
    public String initAllMarketPrice() {
        int totalCount = 0;
        int failCount = 0;
        int pageIndex = 1;

        while (true) {
            List<CsgoGoods> goodsList = csgoGoodsMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CsgoGoods>()
                            .last("LIMIT " + PAGE_SIZE + " OFFSET " + ((pageIndex - 1) * PAGE_SIZE))
            );

            if (goodsList.isEmpty()) {
                break;
            }

            for (CsgoGoods goods : goodsList) {
                try {
                    fetchAndSaveDetail(goods.getGoodsId());
                    totalCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("拉取饰品详情失败: goodsId={}, error={}", goods.getGoodsId(), e.getMessage());
                }
            }

            log.info("第{}批完成, 成功{}, 失败{}", pageIndex, goodsList.size() - failCount, failCount);
            pageIndex++;
        }

        String msg = "初始化完成, 成功" + totalCount + "条, 失败" + failCount + "条";
        log.info("===== {} =====", msg);
        return msg;
    }

    /**
     * 获取单个饰品的市场价格
     */
    public CsgoMarketPrice getMarketPrice(int goodsId) {
        return csgoMarketPriceMapper.selectById(goodsId);
    }

    /**
     * 按饰品ID列表批量初始化市场价格
     */
    public String initByGoodsIds(List<Integer> goodsIds) {
        int successCount = 0;
        int failCount = 0;
        for (Integer goodsId : goodsIds) {
            try {
                fetchAndSaveDetail(goodsId);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("拉取饰品详情失败: goodsId={}, error={}", goodsId, e.getMessage());
            }
        }
        String msg = "初始化完成, 成功" + successCount + "条, 失败" + failCount + "条";
        log.info("===== {} =====", msg);
        return msg;
    }

    private void updateGoodsInfo(CsqaqGoodsDetailResponse.GoodsInfo info) {
        CsgoGoods existing = csgoGoodsMapper.selectById(info.getId());
        if (existing != null) {
            existing.setName(info.getName());
            existing.setExteriorLocalizedName(info.getExteriorLocalizedName());
            existing.setRarityLocalizedName(info.getRarityLocalizedName());
            existing.setImg(info.getImg());
            csgoGoodsMapper.updateById(existing);
        }
    }

    private CsgoMarketPrice convertToPriceEntity(CsqaqGoodsDetailResponse.GoodsInfo info) {
        CsgoMarketPrice entity = new CsgoMarketPrice();
        entity.setGoodsId(info.getId());

        // 平台ID
        entity.setBuffId(info.getBuffId());
        entity.setYyypId(info.getYyypId());
        entity.setC5Id(info.getC5Id());
        entity.setIgxeId(info.getIgxeId());
        entity.setEcoId(info.getEcoId());

        // BUFF价格
        entity.setBuffSellPrice(info.getBuffSellPrice());
        entity.setBuffBuyPrice(info.getBuffBuyPrice());
        entity.setBuffSellNum(info.getBuffSellNum());
        entity.setBuffBuyNum(info.getBuffBuyNum());

        // 悠悠有品
        entity.setYyypSellPrice(info.getYyypSellPrice());
        entity.setYyypSellNum(info.getYyypSellNum());
        entity.setYyypBuyPrice(info.getYyypBuyPrice());
        entity.setYyypBuyNum(info.getYyypBuyNum());
        entity.setYyypLeaseNum(info.getYyypLeaseNum());
        entity.setYyypTransferPrice(info.getYyypTransferPrice());
        entity.setYyypLeasePrice(info.getYyypLeasePrice());
        entity.setYyypLongLeasePrice(info.getYyypLongLeasePrice());
        entity.setYyypLeaseAnnual(info.getYyypLeaseAnnual());
        entity.setYyypLongLeaseAnnual(info.getYyypLongLeaseAnnual());
        entity.setYyypSteamPrice(info.getYyypSteamPrice());

        // Steam
        entity.setSteamSellPrice(info.getSteamSellPrice());
        entity.setSteamSellNum(info.getSteamSellNum());
        entity.setSteamBuyPrice(info.getSteamBuyPrice());
        entity.setSteamBuyNum(info.getSteamBuyNum());
        entity.setSteamBuffBuyConversion(info.getSteamBuffBuyConversion());
        entity.setSteamBuffSellConversion(info.getSteamBuffSellConversion());
        entity.setBuffSteamBuyConversion(info.getBuffSteamBuyConversion());
        entity.setBuffSteamSellConversion(info.getBuffSteamSellConversion());

        // C5GAME
        entity.setC5SellPrice(info.getC5SellPrice());
        entity.setC5SellNum(info.getC5SellNum());
        entity.setC5BuyPrice(info.getC5BuyPrice());
        entity.setC5BuyNum(info.getC5BuyNum());
        entity.setC5LeasePrice(info.getC5LeasePrice());
        entity.setC5LongLeasePrice(info.getC5LongLeasePrice());

        // IGXE
        entity.setIgxeSellPrice(info.getIgxeSellPrice());
        entity.setIgxeSellNum(info.getIgxeSellNum());
        entity.setIgxeBuyPrice(info.getIgxeBuyPrice());
        entity.setIgxeBuyNum(info.getIgxeBuyNum());
        entity.setIgxeLeasePrice(info.getIgxeLeasePrice());
        entity.setIgxeLongLeasePrice(info.getIgxeLongLeasePrice());
        entity.setIgxeLeaseNum(info.getIgxeLeaseNum());

        // ECOSteam
        entity.setEcoSellPrice(info.getEcoSellPrice());
        entity.setEcoSellNum(info.getEcoSellNum());
        entity.setEcoBuyPrice(info.getEcoBuyPrice());
        entity.setEcoBuyNum(info.getEcoBuyNum());

        // R8
        entity.setR8SellPrice(info.getR8SellPrice());
        entity.setR8SellNum(info.getR8SellNum());

        // BUFF涨跌幅
        entity.setSellPriceRate1(info.getSellPriceRate1());
        entity.setSellPriceRate7(info.getSellPriceRate7());
        entity.setSellPriceRate30(info.getSellPriceRate30());
        entity.setSellPriceRate180(info.getSellPriceRate180());
        entity.setSellPrice1(info.getSellPrice1());
        entity.setSellPrice7(info.getSellPrice7());
        entity.setSellPrice30(info.getSellPrice30());
        entity.setSellPrice180(info.getSellPrice180());

        // YYYP涨跌幅
        entity.setYyypSellPriceRate1(info.getYyypSellPriceRate1());
        entity.setYyypSellPriceRate7(info.getYyypSellPriceRate7());
        entity.setYyypSellPriceRate30(info.getYyypSellPriceRate30());
        entity.setYyypSellPriceRate180(info.getYyypSellPriceRate180());
        entity.setYyypSellPrice1(info.getYyypSellPrice1());
        entity.setYyypSellPrice7(info.getYyypSellPrice7());
        entity.setYyypSellPrice30(info.getYyypSellPrice30());
        entity.setYyypSellPrice180(info.getYyypSellPrice180());

        // Steam成交
        entity.setTurnoverNumber(info.getTurnoverNumber());
        entity.setTurnoverAvgPrice(info.getTurnoverAvgPrice());

        // 存世量与热度
        entity.setStatistic(info.getStatistic());
        entity.setRankNum(info.getRankNum());
        entity.setRankNumChange(info.getRankNumChange());

        // 磨损值
        if (info.getMinFloat() != null) {
            entity.setMinFloat(new java.math.BigDecimal(info.getMinFloat()));
        }
        if (info.getMaxFloat() != null) {
            entity.setMaxFloat(new java.math.BigDecimal(info.getMaxFloat()));
        }

        return entity;
    }
}
