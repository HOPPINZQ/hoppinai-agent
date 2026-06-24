package com.hoppinzq.csgo.controller;

import com.hoppinzq.csgo.dto.CsqaqGoodsResponse;
import com.hoppinzq.csgo.entity.CsgoGoods;
import com.hoppinzq.csgo.service.CsgoGoodsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSGO饰品信息接口
 */
@Slf4j
@RestController
@RequestMapping("/api/csgo/goods")
@CrossOrigin(origins = "*")
public class CsgoGoodsController {

    private final CsgoGoodsService csgoGoodsService;

    public CsgoGoodsController(CsgoGoodsService csgoGoodsService) {
        this.csgoGoodsService = csgoGoodsService;
    }

    /**
     * 初始化全量饰品数据：分页拉取直到无数据
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> init() {
        try {
            String result = csgoGoodsService.initAllGoods();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("初始化饰品数据失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 轮询拉取一页饰品数据并保存
     *
     * @param body {"pageIndex": 1}
     */
    @PostMapping("/poll")
    public ResponseEntity<Map<String, Object>> poll(@RequestBody Map<String, Object> body) {
        try {
            int pageIndex = ((Number) body.get("pageIndex")).intValue();

            CsqaqGoodsResponse apiResponse = csgoGoodsService.pollPage(pageIndex);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);

            if (apiResponse != null && apiResponse.getData() != null && apiResponse.getData().getData() != null) {
                int fetchedCount = apiResponse.getData().getData().size();
                result.put("currentPage", apiResponse.getData().getCurrentPage());
                result.put("fetchedCount", fetchedCount);
                result.put("data", apiResponse.getData().getData());
            } else {
                result.put("fetchedCount", 0);
                result.put("data", List.of());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("轮询拉取饰品数据失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 获取数据库中饰品总数
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", csgoGoodsService.getTotalCount());
        return ResponseEntity.ok(result);
    }

    /**
     * 按名称搜索饰品
     */
    @GetMapping("/search")
    public ResponseEntity<List<CsgoGoods>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(csgoGoodsService.searchGoods(keyword));
    }
}
