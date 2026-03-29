package com.hoppinzq.wybuff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.Single;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/**
 * 网易BUFF API接口定义
 */
public interface BuffApi {

    /**
     * 获取流行饰品
     *
     * @return 流行饰品数据
     */
    @GET("/api/index/popular_sell_order")
    Single<ObjectNode> getPopularSellOrder();

    /**
     * 获取饰品价格（市场商品列表）
     *
     * @param game 游戏名
     * @param pageNum 页码
     * @param categoryGroup 类别组（例如：hands为手套，knife为刀）
     * @param quality 质量
     * @param exterior 外观
     * @param tab 标签
     * @return 市场商品列表数据
     */
    @GET("/api/market/goods")
    Single<ObjectNode> getMarketGoods(@Query("game") String game,
                                      @Query("page_num") String pageNum,
                                      @Query("category_group") String categoryGroup,
                                      @Query("rarity") String rarity,
                                      @Query("quality") String quality,
                                      @Query("exterior") String exterior,
                                      @Query("tab") String tab);

    /**
     * 获取具体物品价格（商品在售订单）
     *
     * @param game 游戏名
     * @param goodsId 商品ID
     * @param pageNum 页码
     * @param sortBy 排序方式
     * @param mode 模式
     * @param allowTradableCooldown 允许交易冷却
     * @return 具体物品在售订单数据
     */
    @GET("/api/market/goods/sell_order")
    Single<ObjectNode> getGoodsSellOrder(@Query("game") String game,
                                         @Query("goods_id") String goodsId,
                                         @Query("page_num") String pageNum,
                                         @Query("sort_by") String sortBy,
                                         @Query("mode") String mode,
                                         @Query("allow_tradable_cooldown") String allowTradableCooldown);

    /**
     * 获取物品交易记录
     *
     * @param game 游戏名
     * @param goodsId 商品ID
     * @return 物品交易记录数据
     */
    @GET("/api/market/goods/bill_order")
    Single<ObjectNode> getBillOrder(@Query("game") String game,
                                    @Query("goods_id") String goodsId);

    /**
     * 获取物品成交历史曲线图
     *
     * @param game 游戏名
     * @param goodsId 商品ID
     * @param currency 货币类型
     * @param days 天数
     * @return 物品成交历史曲线图数据
     */
    @GET("/api/market/goods/price_history/buff/v2")
    Single<ObjectNode> getPriceHistory(@Query("game") String game,
                                       @Query("goods_id") String goodsId,
                                       @Query("currency") String currency,
                                       @Query("days") String days);
}
