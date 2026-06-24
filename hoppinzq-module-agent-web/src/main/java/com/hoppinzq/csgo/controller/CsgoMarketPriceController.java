package com.hoppinzq.csgo.controller;

import com.hoppinzq.csgo.dto.CsqaqGoodsDetailResponse;
import com.hoppinzq.csgo.entity.CsgoMarketPrice;
import com.hoppinzq.csgo.service.CsgoMarketPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSGO饰品市场价格接口 (api2)
 */
@Slf4j
@RestController
@RequestMapping("/api/csgo/market-price")
@CrossOrigin(origins = "*")
public class CsgoMarketPriceController {

    private final CsgoMarketPriceService csgoMarketPriceService;

    public CsgoMarketPriceController(CsgoMarketPriceService csgoMarketPriceService) {
        this.csgoMarketPriceService = csgoMarketPriceService;
    }

    /**
     * 拉取单个饰品详情并保存市场价格
     */
    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchDetail(@RequestParam int goodsId) {
        try {
            CsqaqGoodsDetailResponse response = csgoMarketPriceService.fetchAndSaveDetail(goodsId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);

            if (response != null && response.getData() != null && response.getData().getGoodsInfo() != null) {
                result.put("data", response.getData().getGoodsInfo());
            } else {
                result.put("data", null);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("拉取饰品详情失败, goodsId={}", goodsId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 按饰品ID列表批量初始化市场价格
     *
     * @param body {"goodsIds": [7310, 6798, 38]}
     */
    @PostMapping("/init-by-ids")
    public ResponseEntity<Map<String, Object>> initByIds(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> idList = (List<Number>) body.get("goodsIds");
            if (idList == null || idList.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "goodsIds不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            List<Integer> goodsIds = idList.stream().map(Number::intValue).toList();
            String result = csgoMarketPriceService.initByGoodsIds(goodsIds);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("按ID初始化市场价格失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 批量初始化所有饰品的市场价格（遍历csgo_goods表逐个拉取）
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> init() {
        try {
            String result = csgoMarketPriceService.initAllMarketPrice();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("初始化市场价格失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 查询单个饰品的市场价格
     */
    @GetMapping("/get")
    public ResponseEntity<Map<String, Object>> get(@RequestParam int goodsId) {
        CsgoMarketPrice price = csgoMarketPriceService.getMarketPrice(goodsId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", price);
        return ResponseEntity.ok(result);
    }
}
