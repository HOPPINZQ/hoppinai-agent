package com.hoppinzq.wybuff.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.HoppinaiTempReActApplication;
import com.hoppinzq.wybuff.BuffService;
import com.hoppinzq.wybuff.entity.BuffBillOrder;
import com.hoppinzq.wybuff.entity.BuffGoods;
import com.hoppinzq.wybuff.entity.BuffPrice;
import com.hoppinzq.wybuff.entity.BuffPriceHistory;
import com.hoppinzq.wybuff.service.BuffBillOrderService;
import com.hoppinzq.wybuff.service.BuffGoodsService;
import com.hoppinzq.wybuff.service.BuffPriceHistoryService;
import com.hoppinzq.wybuff.service.BuffPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/buff/demo")
public class DemoController {

    @Autowired
    private BuffGoodsService goodsService;
    @Autowired
    private BuffPriceService priceService;
    @Autowired
    private BuffBillOrderService billOrderService;
    @Autowired
    private BuffPriceHistoryService priceHistoryService;
    
    @GetMapping("/demo")
    public void demo() {
        String cookie = "nts_mail_user=zxclae@163.com:-1:1; NTES_P_UTID=a1M9FfCXEcOWA7vgDgSxZMEs60gU0V1T|1758973960; _ntes_nuid=6407d1d4af7dba0b6bbb9cf6bd05cf71; _ntes_nnid=6407d1d4af7dba0b6bbb9cf6bd05cf71,1761980367063; Device-Id=SrOkHZ8nSNjc1cfbrhLE; P_INFO=15028582175|1774359125|1|netease_buff|00&99|null&null&null#shd&370700#10#0|&0|null|15028582175; remember_me=U1094679089|cOe3yXGhYq9yQqIAJTX3IdbPmNygA6ws; Locale-Supported=zh-Hans; game=csgo; session=1-gFqaoDnRb0RUo31sjcoRlS9t2UDRMNjlKHKxtMSYjaSJ2045896041; csrf_token=IjEyNTlhMTc0NTI2MmNkYzdiODU2Y2M1YjcwZDFjOWVlMGU1NTE5NzMi.acP5Kw.y0-oBI_1nGill8luhmDGPAkFCqk";
        BuffService service = new BuffService(cookie);
        try {
            System.out.println("=== BUFF API 测试用例 ===\n");

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for(int i1=1;i1<=5;i1++){
                final int pageNum = i1;
                futures.add(CompletableFuture.runAsync(() -> {
                    testGetMarketGoodsAndSave(service,null,"normal","ancient_weapon","wearcategory2", String.valueOf(pageNum));
                }));
            }

            for(int i1=1;i1<=9;i1++){
                final int pageNum = i1;
                futures.add(CompletableFuture.runAsync(() -> {
                    testGetMarketGoodsAndSave(service,null,"normal","legendary_weapon","wearcategory2", String.valueOf(pageNum));
                }));
            }
            for(int i1=1;i1<=5;i1++){
                final int pageNum = i1;
                futures.add(CompletableFuture.runAsync(() -> {
                    testGetMarketGoodsAndSave(service,null,"normal","mythical_weapon","wearcategory2", String.valueOf(pageNum));
                }));
            }
            for(int i1=1;i1<=6;i1++){
                final int pageNum = i1;
                futures.add(CompletableFuture.runAsync(() -> {
                    testGetMarketGoodsAndSave(service,null,"normal","rare_weapon","wearcategory2", String.valueOf(pageNum));
                }));
            }
            for(int i1=1;i1<=3;i1++){
                final int pageNum = i1;
                futures.add(CompletableFuture.runAsync(() -> {
                    testGetMarketGoodsAndSave(service,null,"normal","uncommon_weapon","wearcategory2", String.valueOf(pageNum));
                }));
            }


            for(int i1=1;i1<=16;i1++){
                final int pageNum = i1;
                futures.add(CompletableFuture.runAsync(() -> {
                    testGetMarketGoodsAndSave(service,"knife","unusual","ancient_weapon","wearcategory2", String.valueOf(pageNum));
                }));
            }
            for(int i1=1;i1<=5;i1++){
                final int pageNum = i1;
                futures.add(CompletableFuture.runAsync(() -> {
                    testGetMarketGoodsAndSave(service,"hands","unusual","ancient_weapon","wearcategory2", String.valueOf(pageNum));
                }));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            System.out.println("所有任务执行完成！");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            service.shutdownExecutor();
        }
    }

    private void testGetMarketGoodsAndSave(BuffService service,String categoryGroup,String quality,String rarity,String exterior, String pageNum) {
        System.out.println("2. 测试获取饰品价格（市场商品列表）并保存到数据库");
        try {
            JsonNode result = service.getMarketGoods("csgo", pageNum, categoryGroup,rarity, quality, exterior, "selling");
            System.out.println("API调用成功，开始解析数据...");

            if (result.has("data") && result.get("data").has("items")) {
                JsonNode items = result.get("data").get("items");
                System.out.println("找到 " + items.size() + " 个物品");

                int savedCount = 0;
                int updatedCount = 0;

                for (JsonNode item : items) {
                    Integer goodsId = item.get("id").asInt();
                    String name = item.get("name").asText();

                    BuffGoods existingGoods = goodsService.getBaseMapper().selectById(goodsId);

                    BuffGoods goods = new BuffGoods();
                    goods.setId(goodsId);
                    goods.setName(name);
                    goods.setMarketHashName(item.has("market_hash_name") ? item.get("market_hash_name").asText() : null);
                    goods.setShortName(item.has("short_name") ? item.get("short_name").asText() : null);
                    goods.setUpdateTime(LocalDateTime.now());
                    JsonNode goodsInfo = item.get("goods_info");
                    goods.setIconUrl(goodsInfo.has("icon_url") ? goodsInfo.get("icon_url").asText() : null);
                    goods.setInfo(String.valueOf(goodsInfo));
                    if (existingGoods == null) {
                        goods.setCreateTime(LocalDateTime.now());
                        goodsService.save(goods);
                        savedCount++;
                        System.out.println("新增物品: " + name + " (ID: " + goodsId + ")");
                    } else {
                        goods.setId(existingGoods.getId());
                        goods.setCreateTime(existingGoods.getCreateTime());
                        goodsService.updateById(goods);
                        updatedCount++;
                        System.out.println("更新物品: " + name + " (ID: " + goodsId + ")");
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
                    price.setSteamPriceCny(goodsInfo.has("steam_price_cny") ? new BigDecimal(goodsInfo.get("steam_price_cny").asText()) : null);

                    price.setCreateTime(LocalDateTime.now());
                    priceService.save(price);
                    System.out.println("  保存价格记录: 最低售价=" + price.getSellMinPrice() + ", 参考售价=" + price.getSellReferencePrice() + ", 时间=" + price.getCreateTime());
                    CompletableFuture.runAsync(() -> {
                        testGetPriceHistoryAndSave(service,goodsId);
                    });
                }

                System.out.println("数据保存完成！新增物品: " + savedCount + ", 更新物品: " + updatedCount + ", 保存价格记录: " + items.size());
            }

            System.out.println("成功！\n");
        } catch (Exception e) {
            System.err.println("失败: " + e.getMessage());
            e.printStackTrace();
            System.out.println();
        }
    }

    private void testGetPriceHistoryAndSave(BuffService service,Integer goodId) {
        System.out.println("5. 测试获取物品成交历史曲线图并保存到数据库");
        try {
            JsonNode result = service.getPriceHistory("csgo", String.valueOf(goodId), "CNY", "30");
            System.out.println("API调用成功，开始解析数据...");

            if (result.has("data")) {
                JsonNode data = result.get("data");
                System.out.println("找到价格历史数据");

                if (data.has("lines") && data.get("lines").isArray()) {
                    JsonNode lines = data.get("lines");
                    System.out.println("找到 " + lines.size() + " 种历史数据类型");

                    int savedCount = 0;
                    String currency = data.has("currency") ? data.get("currency").asText() : "CNY";

                    for (JsonNode line : lines) {
                        String priceType = line.has("key") ? line.get("key").asText() : "";
                        String name = line.has("name") ? line.get("name").asText() : "";
                        String chartType = line.has("chart_type") ? line.get("chart_type").asText() : "price";

                        System.out.println("处理历史数据类型: " + name + " (" + priceType + ")");

                        if (line.has("points") && line.get("points").isArray()) {
                            JsonNode points = line.get("points");
                            System.out.println("  找到 " + points.size() + " 个数据点");

                            for (JsonNode point : points) {
                                if (point.isArray() && point.size() >= 2) {
                                    Long timestamp = point.get(0).asLong();

                                    BuffPriceHistory existingHistory = priceHistoryService.lambdaQuery()
                                            .eq(BuffPriceHistory::getGoodsId, goodId)
                                            .eq(BuffPriceHistory::getPriceType, priceType)
                                            .eq(BuffPriceHistory::getTimestamp, timestamp)
                                            .one();

                                    BuffPriceHistory priceHistory;
                                    if (existingHistory != null) {
                                        priceHistory = existingHistory;
                                        System.out.println("    更新已有记录: goodsId=" + priceHistory.getGoodsId() + ", priceType=" + priceType + ", timestamp=" + timestamp);
                                    } else {
                                        priceHistory = new BuffPriceHistory();
                                        priceHistory.setGoodsId(goodId);
                                        priceHistory.setPriceType(priceType);
                                        priceHistory.setTimestamp(timestamp);
                                        priceHistory.setCurrency(currency);
                                        priceHistory.setCreateTime(LocalDateTime.now());
                                        System.out.println("    新增记录: goodsId=" + priceHistory.getGoodsId() + ", priceType=" + priceType + ", timestamp=" + timestamp);
                                    }

                                    if ("price".equals(chartType)) {
                                        priceHistory.setPrice(new BigDecimal(point.get(1).asText()));
                                    } else if ("number".equals(chartType)) {
                                        priceHistory.setQuantity(point.get(1).asLong());
                                    }

                                    priceHistoryService.saveOrUpdate(priceHistory);
                                    savedCount++;
                                }
                            }
                        }
                    }

                    System.out.println("数据保存完成！保存价格历史记录: " + savedCount);
                }
            }

            System.out.println("成功！\n");
        } catch (Exception e) {
            System.err.println("失败: " + e.getMessage());
            e.printStackTrace();
            System.out.println();
        }
    }
}
