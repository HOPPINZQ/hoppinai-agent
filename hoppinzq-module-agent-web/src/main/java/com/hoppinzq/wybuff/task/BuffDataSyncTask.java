package com.hoppinzq.wybuff.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.hoppinzq.wybuff.BuffService;
import com.hoppinzq.wybuff.entity.BuffGoods;
import com.hoppinzq.wybuff.entity.BuffPrice;
import com.hoppinzq.wybuff.entity.BuffPriceHistory;
import com.hoppinzq.wybuff.mapper.BuffApiRetryRecordMapper;
import com.hoppinzq.wybuff.service.BuffGoodsService;
import com.hoppinzq.wybuff.service.BuffPriceHistoryService;
import com.hoppinzq.wybuff.service.BuffPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BuffDataSyncTask {

    @Autowired
    private BuffGoodsService goodsService;

    @Autowired
    private BuffPriceService priceService;

    @Autowired
    private BuffPriceHistoryService priceHistoryService;

    @Autowired
    private BuffApiRetryRecordMapper retryRecordMapper;

    private static final String COOKIE = "nts_mail_user=zxclae@163.com:-1:1; NTES_P_UTID=a1M9FfCXEcOWA7vgDgSxZMEs60gU0V1T|1758973960; _ntes_nuid=6407d1d4af7dba0b6bbb9cf6bd05cf71; _ntes_nnid=6407d1d4af7dba0b6bbb9cf6bd05cf71,1761980367063; Device-Id=SrOkHZ8nSNjc1cfbrhLE; P_INFO=15028582175|1774359125|1|netease_buff|00&99|null&null&null#shd&370700#10#0|&0|null|15028582175; Locale-Supported=zh-Hans; game=csgo; qr_code_verify_ticket=34bh56L5d3bc182aeb27ee3fb07a1d0e7652; remember_me=U1089492179|euXtYbfyVQ3dJ9MRam80c4gzquMNKnoS; session=1-OL21478Pemj5i5IZQ198pJvv0ZyvcBQutXg0UMIB6u7a2017381259; csrf_token=ImIyY2YzNjE1MTBlN2E0OGM5MDM5OTQzOTBiNzAxMDcyNWQzMWJjODUi.acZ4UA.dklxUObXBgeNlUm6WOtiDcqd2Tk";

    @Scheduled(cron = "0 0 */12 * * ?")
    public void syncAllPriceHistory() {
        log.info("=== 开始执行价格历史同步任务 ===");
        BuffService service = null;
        try {
            service = new BuffService(COOKIE, retryRecordMapper);

            List<BuffGoods> allGoods = goodsService.list();
            log.info("从数据库查询到 {} 个物品", allGoods.size());

            for (BuffGoods goods : allGoods) {
                Thread.sleep(1000);
                if (goods.getId() != null) {
                    syncPriceHistory(service, goods.getId());
                }
            }

            log.info("=== 价格历史同步任务完成 ===");
        } catch (Exception e) {
            log.error("价格历史同步任务执行失败", e);
        } finally {
            if (service != null) {
                service.shutdownExecutor();
            }
        }
    }

    //@Scheduled(cron = "0 */10 * * * ?")
    public void demo() {
        log.info("=== 开始执行BUFF数据异步任务 ===");
        BuffService service = null;
        try {
            service = new BuffService(COOKIE, retryRecordMapper);

            int totalSuccess = 0;
            int totalFail = 0;

            totalSuccess += syncMarketGoods(service, null, "normal", "ancient_weapon", "wearcategory2", 5).getSuccessCount();
            Thread.sleep(5*1000);
            totalSuccess += syncMarketGoods(service, null, "normal", "legendary_weapon", "wearcategory2", 9).getSuccessCount();
            Thread.sleep(5*1000);
            totalSuccess += syncMarketGoods(service, null, "normal", "mythical_weapon", "wearcategory2", 5).getSuccessCount();
            Thread.sleep(5*1000);
            totalSuccess += syncMarketGoods(service, null, "normal", "rare_weapon", "wearcategory2", 6).getSuccessCount();
            Thread.sleep(5*1000);
            totalSuccess += syncMarketGoods(service, null, "normal", "uncommon_weapon", "wearcategory2", 3).getSuccessCount();
            Thread.sleep(5*1000);
            totalSuccess += syncMarketGoods(service, "knife", "unusual", "ancient_weapon", "wearcategory2", 16).getSuccessCount();
            Thread.sleep(5*1000);
            totalSuccess += syncMarketGoods(service, "hands", "unusual", "ancient_weapon", "wearcategory2", 5).getSuccessCount();
            Thread.sleep(5*1000);
            log.info("=== BUFF数据同步任务完成 | 成功: {}, 失败: {} ===", totalSuccess, totalFail);

        } catch (Exception e) {
            log.error("BUFF数据同步任务执行失败", e);
        } finally {
            if (service != null) {
                service.shutdownExecutor();
            }
        }
    }

    //@Scheduled(cron = "0 */3 * * * ?")
    public void syncBuffData() {
        log.info("=== 开始执行BUFF数据同步任务 ===");
        BuffService service = null;
        try {
            service = new BuffService(COOKIE, retryRecordMapper);

            List<CompletableFuture<SyncResult>> futures = new ArrayList<>();

            BuffService finalService = service;
            futures.add(CompletableFuture.supplyAsync(() -> syncMarketGoods(finalService, null, "normal", "ancient_weapon", "wearcategory2", 5)));
            Thread.sleep(5*1000);
            futures.add(CompletableFuture.supplyAsync(() -> syncMarketGoods(finalService, null, "normal", "legendary_weapon", "wearcategory2", 9)));
            Thread.sleep(5*1000);
            futures.add(CompletableFuture.supplyAsync(() -> syncMarketGoods(finalService, null, "normal", "mythical_weapon", "wearcategory2", 5)));
            Thread.sleep(5*1000);
            futures.add(CompletableFuture.supplyAsync(() -> syncMarketGoods(finalService, null, "normal", "rare_weapon", "wearcategory2", 6)));
            Thread.sleep(5*1000);
            futures.add(CompletableFuture.supplyAsync(() -> syncMarketGoods(finalService, null, "normal", "uncommon_weapon", "wearcategory2", 3)));
            Thread.sleep(5*1000);
            futures.add(CompletableFuture.supplyAsync(() -> syncMarketGoods(finalService, "knife", "unusual", "ancient_weapon", "wearcategory2", 16)));
            Thread.sleep(5*1000);
            futures.add(CompletableFuture.supplyAsync(() -> syncMarketGoods(finalService, "hands", "unusual", "ancient_weapon", "wearcategory2", 5)));

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            int totalSuccess = futures.stream()
                    .mapToInt(future -> future.join().getSuccessCount())
                    .sum();
            int totalFail = futures.stream()
                    .mapToInt(future -> future.join().getFailCount())
                    .sum();

            log.info("=== BUFF数据同步任务完成 | 成功: {}, 失败: {} ===", totalSuccess, totalFail);

        } catch (Exception e) {
            log.error("BUFF数据同步任务执行失败", e);
        } finally {
            if (service != null) {
                service.shutdownExecutor();
            }
        }
    }

    private SyncResult syncMarketGoods(BuffService service, String categoryGroup, String quality, String rarity, String exterior, int totalPages) {
        SyncResult result = new SyncResult();
        try {
            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                Thread.sleep(2000);
                JsonNode apiResult = service.getMarketGoods("csgo", String.valueOf(pageNum), categoryGroup, rarity, quality, exterior, "selling");
                if (!checkApiCode(apiResult)) {
                    log.error("API返回错误，停止同步。Page: {}, categoryGroup: {}, quality: {}, rarity: {}", pageNum, categoryGroup, quality, rarity);
                    result.addFail();
                    return result;
                }

                if (!apiResult.has("data") || !apiResult.get("data").has("items")) {
                    log.warn("API返回数据格式异常，Page: {}", pageNum);
                    result.addFail();
                    continue;
                }

                JsonNode items = apiResult.get("data").get("items");
                if (items == null || !items.isArray()) {
                    log.warn("items字段不存在或不是数组，Page: {}", pageNum);
                    result.addFail();
                    continue;
                }

                List<BuffGoods> goodsToSave = new ArrayList<>();
                List<BuffGoods> goodsToUpdate = new ArrayList<>();
                List<BuffPrice> pricesToSave = new ArrayList<>();

                Set<Integer> goodsIds = new HashSet<>();

                for (JsonNode item : items) {
                    if (!item.has("id")) {
                        continue;
                    }

                    Integer goodsId = item.get("id").asInt();
                    goodsIds.add(goodsId);
                    BuffGoods existingGoods = goodsService.getBaseMapper().selectById(goodsId);

                    BuffGoods goods = new BuffGoods();
                    goods.setId(goodsId);
                    goods.setName(item.has("name") ? item.get("name").asText() : null);
                    goods.setMarketHashName(item.has("market_hash_name") ? item.get("market_hash_name").asText() : null);
                    goods.setShortName(item.has("short_name") ? item.get("short_name").asText() : null);
                    goods.setUpdateTime(LocalDateTime.now());

                    JsonNode goodsInfo = item.get("goods_info");
                    if (goodsInfo != null) {
                        goods.setIconUrl(goodsInfo.has("icon_url") ? goodsInfo.get("icon_url").asText() : null);
                        goods.setInfo(String.valueOf(goodsInfo));
                    }

                    if (existingGoods == null) {
                        goods.setCreateTime(LocalDateTime.now());
                        goodsToSave.add(goods);
                    } else {
                        goods.setCreateTime(existingGoods.getCreateTime());
                        goodsToUpdate.add(goods);
                    }

                    BuffPrice price = new BuffPrice();
                    price.setGoodsId(goodsId);
                    price.setSellMinPrice(item.has("sell_min_price") ? new BigDecimal(item.get("sell_min_price").asText()) : null);
                    price.setBuyMaxPrice(item.has("buy_max_price") ? new BigDecimal(item.get("buy_max_price").asText()) : null);
                    price.setSellReferencePrice(item.has("sell_reference_price") ? new BigDecimal(item.get("sell_reference_price").asText()) : null);
                    price.setQuickPrice(item.has("quick_price") ? new BigDecimal(item.get("quick_price").asText()) : null);
                    price.setMarketMinPrice(item.has("market_min_price") ? new BigDecimal(item.get("market_min_price").asText()) : null);
                    price.setMinRentUnitPrice(item.has("min_rent_unit_price") ? new BigDecimal(item.get("min_rent_unit_price").asText()) : null);
                    price.setRentUnitReferencePrice(item.has("rent_unit_reference_price") ? new BigDecimal(item.get("rent_unit_reference_price").asText()) : null);
                    price.setMinSecurityPrice(item.has("min_security_price") ? new BigDecimal(item.get("min_security_price").asText()) : null);
                    price.setSellNum(item.has("sell_num") ? item.get("sell_num").asInt() : null);
                    price.setBuyNum(item.has("buy_num") ? item.get("buy_num").asInt() : null);
                    price.setRentNum(item.has("rent_num") ? item.get("rent_num").asInt() : null);
                    price.setTransactedNum(item.has("transacted_num") ? item.get("transacted_num").asInt() : null);
                    if (goodsInfo != null) {
                        price.setSteamPriceCny(goodsInfo.has("steam_price_cny") ? new BigDecimal(goodsInfo.get("steam_price_cny").asText()) : null);
                    }
                    price.setCreateTime(LocalDateTime.now());
                    pricesToSave.add(price);
                }

                if (!goodsToSave.isEmpty()) {
                    goodsService.saveBatch(goodsToSave);
                    log.info("批量新增物品: {}", goodsToSave.size());
                }
                if (!goodsToUpdate.isEmpty()) {
                    goodsService.updateBatchById(goodsToUpdate);
                    log.info("批量更新物品: {}", goodsToUpdate.size());
                }
                if (!pricesToSave.isEmpty()) {
                    priceService.saveBatch(pricesToSave);
                    log.info("批量保存价格: {}", pricesToSave.size());
                }
                result.addSuccess();
            }

        } catch (Exception e) {
            log.error("同步市场商品数据失败，categoryGroup: {}, quality: {}, rarity: {}", categoryGroup, quality, rarity, e);
            result.addFail();
        }
        return result;
    }

    private void syncPriceHistory(BuffService service, Integer goodsId) {
        try {
            JsonNode apiResult = service.getPriceHistory("csgo", String.valueOf(goodsId), "CNY", "30");

            if (!checkApiCode(apiResult)) {
                log.error("价格历史API返回错误，goodsId: {}", goodsId);
                return;
            }

            if (!apiResult.has("data")) {
                log.warn("价格历史API返回数据格式异常，goodsId: {}", goodsId);
                return;
            }

            JsonNode data = apiResult.get("data");

            if (!data.has("lines") || !data.get("lines").isArray()) {
                log.warn("价格历史lines字段不存在或不是数组，goodsId: {}", goodsId);
                return;
            }

            JsonNode lines = data.get("lines");
            String currency = data.has("currency") ? data.get("currency").asText() : "CNY";

            List<BuffPriceHistory> priceHistoryToSave = new ArrayList<>();
            List<BuffPriceHistory> priceHistoryToUpdate = new ArrayList<>();

            for (JsonNode line : lines) {
                if (!line.has("key")) {
                    continue;
                }

                String priceType = line.get("key").asText();
                String chartType = line.has("chart_type") ? line.get("chart_type").asText() : "price";

                if (!line.has("points") || !line.get("points").isArray()) {
                    continue;
                }

                JsonNode points = line.get("points");

                for (JsonNode point : points) {
                    if (!point.isArray() || point.size() < 2) {
                        continue;
                    }

                    Long timestamp = point.get(0).asLong();

                    BuffPriceHistory existingHistory = priceHistoryService.lambdaQuery()
                            .eq(BuffPriceHistory::getGoodsId, goodsId)
                            .eq(BuffPriceHistory::getPriceType, priceType)
                            .eq(BuffPriceHistory::getTimestamp, timestamp)
                            .one();

                    BuffPriceHistory priceHistory;
                    if (existingHistory != null) {
                        priceHistory = existingHistory;
                        if ("price".equals(chartType)) {
                            priceHistory.setPrice(new BigDecimal(point.get(1).asText()));
                        } else if ("number".equals(chartType)) {
                            priceHistory.setQuantity(point.get(1).asLong());
                        }
                        priceHistoryToUpdate.add(priceHistory);
                    } else {
                        priceHistory = new BuffPriceHistory();
                        priceHistory.setGoodsId(goodsId);
                        priceHistory.setPriceType(priceType);
                        priceHistory.setTimestamp(timestamp);
                        priceHistory.setCurrency(currency);
                        priceHistory.setCreateTime(LocalDateTime.now());
                        if ("price".equals(chartType)) {
                            priceHistory.setPrice(new BigDecimal(point.get(1).asText()));
                        } else if ("number".equals(chartType)) {
                            priceHistory.setQuantity(point.get(1).asLong());
                        }
                        priceHistoryToSave.add(priceHistory);
                    }
                }
            }

            if (!priceHistoryToSave.isEmpty()) {
                priceHistoryService.saveBatch(priceHistoryToSave);
                log.info("批量新增价格历史: {}, goodsId: {}", priceHistoryToSave.size(), goodsId);
            }
            if (!priceHistoryToUpdate.isEmpty()) {
                priceHistoryService.updateBatchById(priceHistoryToUpdate);
                log.info("批量更新价格历史: {}, goodsId: {}", priceHistoryToUpdate.size(), goodsId);
            }

        } catch (Exception e) {
            log.error("同步价格历史数据失败，goodsId: {}", goodsId, e);
        }
    }

    private boolean checkApiCode(JsonNode apiResult) {
        if (!apiResult.has("code")) {
            log.error("API返回数据中没有code字段");
            return false;
        }
        String code = apiResult.get("code").asText();
        if (!"OK".equals(code)) {
            log.error("API返回code不是OK，code: {}", code);
            return false;
        }
        return true;
    }

    private static class SyncResult {
        private int successCount = 0;
        private int failCount = 0;

        public void addSuccess() {
            successCount++;
        }

        public void addFail() {
            failCount++;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailCount() {
            return failCount;
        }
    }
}
