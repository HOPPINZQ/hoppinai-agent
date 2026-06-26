package com.hoppinzq.csgo.client;

import com.hoppinzq.csgo.dto.CsqaqGoodsDetailResponse;
import com.hoppinzq.csgo.dto.CsqaqGoodsRequest;
import com.hoppinzq.csgo.dto.CsqaqGoodsResponse;
import io.reactivex.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * CSQAQ饰品信息API接口定义 (api.csqaq.com)
 */
public interface CsqaqInfoApi {

    /**
     * 分页获取饰品列表信息
     */
    @POST("/api/v1/info/get_page_list")
    Single<CsqaqGoodsResponse> getPageList(@Body CsqaqGoodsRequest request);

    /**
     * 获取单件饰品详情
     */
    @GET("/api/v1/info/good")
    Single<CsqaqGoodsDetailResponse> getGoodsDetail(@Query("id") int goodsId);
}
