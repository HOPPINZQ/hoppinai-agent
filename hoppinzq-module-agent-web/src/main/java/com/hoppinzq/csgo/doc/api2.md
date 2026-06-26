# 获取单件饰品详情

## OpenAPI Specification

```yaml
openapi: 3.0.1
info:
  title: ''
  version: 1.0.0
paths:
  /api/v1/info/good:
    get:
      summary: 获取单件饰品详情
      deprecated: false
      description: >-
        🥇 获取单件饰品的详细数据接口，需用饰品good_id进行请求，good_id获取接口为
        [获取饰品的ID信息](http://docs.csqaq.com/api-187131777)


        该接口可获取的数据参考网站页面：https://csqaq.com/goods/135


        :::highlight yellow 

        **以下平台数据均支持：**


        - 网易BUFF、悠悠有品、Steam市场、C5GAME、IGXE、ECOSteam、R8GAME

        :::
      operationId: __good_____api_v1_info_good_get
      tags:
        - 接口详情/饰品详情
        - api
      parameters:
        - name: id
          in: query
          description: 本站记录的饰品id
          required: true
          example: 7310
          schema:
            title: Id
            type: integer
        - name: ApiToken
          in: header
          description: 用户请求令牌（请自行申请）
          required: true
          example: '{{PRO_TOKEN}}'
          schema:
            type: string
            default: '{{PRO_TOKEN}}'
      responses:
        '200':
          description: ''
          content:
            application/json:
              schema:
                type: object
                properties:
                  code:
                    type: integer
                    title: 响应状态码
                  data:
                    type: object
                    properties:
                      goods_info:
                        type: object
                        properties:
                          id:
                            type: integer
                            title: 饰品good_id
                          turnover_number:
                            type: integer
                            title: steam成交量
                          turnover_avg_price:
                            type: integer
                            title: steam成交均价
                            description: 单位$
                          period_at:
                            type: string
                            title: steam成交量所属时间段
                          buff_id:
                            type: integer
                            title: buff平台饰品id
                          yyyp_id:
                            type: integer
                            title: yyyp平台饰品id
                          name:
                            type: string
                            title: 饰品中文名称
                          market_hash_name:
                            type: string
                            title: steam市场唯一英文名称
                          buff_sell_price:
                            type: number
                            title: buff在售价
                          buff_buy_price:
                            type: integer
                            title: buff求购价
                          buff_sell_num:
                            type: integer
                            title: buff在售数量
                          buff_buy_num:
                            type: integer
                            title: buff求购数量
                          yyyp_sell_price:
                            type: integer
                            title: yyyp在售价
                          yyyp_lease_num:
                            type: integer
                            title: yyyp在租数量
                          yyyp_transfer_price:
                            type: integer
                            title: yyyp过户底价
                          yyyp_lease_price:
                            type: number
                            title: yyyp短租价格
                          yyyp_long_lease_price:
                            type: integer
                            title: yyyp长租价格
                          yyyp_lease_annual:
                            type: number
                            title: yyyp短租年收益率
                          yyyp_long_lease_annual:
                            type: number
                            title: yyyp长租年收益率
                          yyyp_sell_num:
                            type: integer
                            title: yyyp在售数量
                          yyyp_steam_price:
                            type: number
                            title: yyyp的steam指导价
                          yyyp_buy_num:
                            type: integer
                            title: yyyp求购数量
                          yyyp_buy_price:
                            type: integer
                            title: yyyp求购价
                          sell_price_rate_1:
                            type: number
                            title: 近1日涨跌幅
                            description: 单位%
                          sell_price_rate_7:
                            type: number
                            title: 近7日涨跌幅
                            description: 单位%
                          sell_price_rate_30:
                            type: number
                            title: 近30日涨跌幅
                            description: 单位%
                          sell_price_rate_180:
                            type: number
                            title: 近180日涨跌幅
                            description: 单位%
                          sell_price_1:
                            type: number
                            title: 近1日涨跌量
                          sell_price_7:
                            type: number
                            title: 近7日涨跌量
                          sell_price_30:
                            type: number
                            title: 近30日涨跌量
                          sell_price_180:
                            type: number
                            title: 近180日涨跌量
                          steam_sell_price:
                            type: number
                            title: steam在售价
                          steam_sell_num:
                            type: integer
                            title: steam在售数量
                          steam_buy_price:
                            type: number
                            title: steam求购价
                          steam_buy_num:
                            type: integer
                            title: steam求购数量
                          steam_buff_buy_conversion:
                            type: number
                            title: Steam求购挂刀比例
                          steam_buff_sell_conversion:
                            type: number
                            title: Steam在售挂刀比例
                          buff_steam_buy_conversion:
                            type: number
                            title: BUFF求购套现比例
                          buff_steam_sell_conversion:
                            type: number
                            title: BUFF售价套现比例
                          type_localized_name:
                            type: string
                            title: 饰品大类
                            description: 手套、匕首、音乐盒等
                          statistic:
                            type: integer
                            title: 存世量
                          img:
                            type: string
                            title: 饰品图片
                          updated_at:
                            type: string
                            title: 数据更新时间
                          rarity_localized_name:
                            type: string
                            title: 饰品品质
                            description: 普通级、受限、非凡、隐秘等
                          quality_localized_name:
                            type: string
                            title: 饰品类别
                            description: ★、普通、★ StatTrak™、纪念品等
                          exterior_localized_name:
                            type: string
                            title: 饰品磨损
                            description: 崭新出厂、略有磨损等
                          group_hash_name:
                            type: string
                            title: 所属类型名称
                          rank_num:
                            type: string
                            title: 热度排名
                          rank_num_change:
                            type: string
                            title: 热度排名变化（较上个周期）
                          def_index:
                            type: integer
                            title: 类型编号
                          paint_index:
                            type: integer
                            title: 皮肤编号
                          c5_sell_price:
                            type: number
                            title: c5game在售价
                          c5_sell_num:
                            type: integer
                            title: c5game在售数量
                          c5_lease_price:
                            type: number
                            title: c5game短租价格
                          c5_long_lease_price:
                            type: number
                            title: c5game长租价格
                          min_float:
                            type: string
                            title: 最小磨损值
                            nullable: true
                          max_float:
                            type: string
                            title: 最大磨损值
                            nullable: true
                          c5_id:
                            type: string
                            title: c5game平台饰品id
                          igxe_id:
                            type: string
                            title: igxe平台饰品id
                          igxe_sell_price:
                            type: number
                            title: igxe在售价
                          igxe_sell_num:
                            type: integer
                            title: igxe在售数量
                          igxe_lease_price:
                            type: number
                            title: igxe短租价格
                          igxe_long_lease_price:
                            type: number
                            title: igxe长租价格
                          igxe_lease_num:
                            type: integer
                            title: igxe在租数量
                          igxe_buy_price:
                            type: number
                            title: igxe求购价格
                          igxe_buy_num:
                            type: integer
                            title: igxe求购数量
                          eco_id:
                            type: string
                            title: eco平台饰品id
                          eco_sku_id:
                            type: string
                            title: eco sku id（跳转使用）
                          eco_sell_price:
                            type: number
                            title: ecosteam在售价格
                          eco_sell_num:
                            type: integer
                            title: ecosteam在售数量
                          eco_buy_price:
                            type: number
                            title: ecosteam求购价格
                          eco_buy_num:
                            type: integer
                            title: ecosteam求购数量
                          c5_buy_price:
                            type: number
                            title: c5game求购价格
                          c5_buy_num:
                            type: integer
                            title: c5game求购数量
                        required:
                          - id
                          - turnover_number
                          - turnover_avg_price
                          - period_at
                          - buff_id
                          - yyyp_id
                          - name
                          - market_hash_name
                          - buff_sell_price
                          - buff_buy_price
                          - buff_sell_num
                          - buff_buy_num
                          - yyyp_sell_price
                          - yyyp_lease_num
                          - yyyp_transfer_price
                          - yyyp_lease_price
                          - yyyp_long_lease_price
                          - yyyp_lease_annual
                          - yyyp_long_lease_annual
                          - yyyp_sell_num
                          - yyyp_steam_price
                          - yyyp_buy_num
                          - yyyp_buy_price
                          - sell_price_rate_1
                          - sell_price_rate_7
                          - sell_price_rate_30
                          - sell_price_rate_180
                          - sell_price_1
                          - sell_price_7
                          - sell_price_30
                          - sell_price_180
                          - steam_sell_price
                          - steam_sell_num
                          - steam_buy_price
                          - steam_buy_num
                          - steam_buff_buy_conversion
                          - steam_buff_sell_conversion
                          - buff_steam_buy_conversion
                          - buff_steam_sell_conversion
                          - type_localized_name
                          - statistic
                          - img
                          - updated_at
                          - rarity_localized_name
                          - quality_localized_name
                          - exterior_localized_name
                          - group_hash_name
                          - rank_num
                          - rank_num_change
                          - def_index
                          - paint_index
                          - c5_sell_price
                          - c5_sell_num
                          - c5_lease_price
                          - c5_long_lease_price
                          - min_float
                          - max_float
                          - c5_id
                          - igxe_id
                          - igxe_sell_price
                          - igxe_sell_num
                          - igxe_lease_price
                          - igxe_long_lease_price
                          - igxe_lease_num
                          - igxe_buy_price
                          - igxe_buy_num
                          - eco_id
                          - eco_sku_id
                          - eco_sell_price
                          - eco_sell_num
                          - eco_buy_price
                          - eco_buy_num
                          - c5_buy_price
                          - c5_buy_num
                        x-apifox-orders:
                          - id
                          - turnover_number
                          - turnover_avg_price
                          - period_at
                          - buff_id
                          - yyyp_id
                          - name
                          - market_hash_name
                          - buff_sell_price
                          - buff_buy_price
                          - buff_sell_num
                          - buff_buy_num
                          - yyyp_sell_price
                          - yyyp_lease_num
                          - yyyp_transfer_price
                          - yyyp_lease_price
                          - yyyp_long_lease_price
                          - yyyp_lease_annual
                          - yyyp_long_lease_annual
                          - yyyp_sell_num
                          - yyyp_steam_price
                          - yyyp_buy_num
                          - yyyp_buy_price
                          - sell_price_rate_1
                          - sell_price_rate_7
                          - sell_price_rate_30
                          - sell_price_rate_180
                          - sell_price_1
                          - sell_price_7
                          - sell_price_30
                          - sell_price_180
                          - steam_sell_price
                          - steam_sell_num
                          - steam_buy_price
                          - steam_buy_num
                          - steam_buff_buy_conversion
                          - steam_buff_sell_conversion
                          - buff_steam_buy_conversion
                          - buff_steam_sell_conversion
                          - type_localized_name
                          - statistic
                          - img
                          - updated_at
                          - rarity_localized_name
                          - quality_localized_name
                          - exterior_localized_name
                          - group_hash_name
                          - rank_num
                          - rank_num_change
                          - def_index
                          - paint_index
                          - c5_id
                          - c5_sell_price
                          - c5_sell_num
                          - c5_buy_price
                          - c5_buy_num
                          - c5_lease_price
                          - c5_long_lease_price
                          - igxe_id
                          - igxe_sell_price
                          - igxe_sell_num
                          - igxe_lease_price
                          - igxe_long_lease_price
                          - igxe_lease_num
                          - igxe_buy_price
                          - igxe_buy_num
                          - eco_id
                          - eco_sku_id
                          - eco_sell_price
                          - eco_sell_num
                          - eco_buy_price
                          - eco_buy_num
                          - min_float
                          - max_float
                        title: 饰品信息
                      button_list:
                        type: array
                        items:
                          type: object
                          properties:
                            id:
                              type: integer
                              title: 饰品good_id
                            name:
                              type: string
                              title: 磨损名称
                            current:
                              type: boolean
                              title: 是否为当前饰品
                            switch:
                              type: boolean
                              title: 是否为切换类别的按钮
                          required:
                            - id
                            - name
                            - current
                            - switch
                          x-apifox-orders:
                            - id
                            - name
                            - current
                            - switch
                        title: 按钮列表
                        description: 同类饰品不同磨损类型的切换列表
                      dpl:
                        type: array
                        items:
                          type: object
                          properties:
                            key:
                              type: integer
                              title: 多普勒id
                            label:
                              type: string
                              title: 中文名称
                            value:
                              type: string
                              title: 英文数值
                            short_name_en:
                              type: string
                              title: hash名称
                            buff_sell_price:
                              type: string
                              title: buff在售价
                            buff_buy_price:
                              type: string
                              title: buff求购价
                            def_index:
                              type: string
                              title: 类型编号
                            paint_index:
                              type: string
                              title: 皮肤编号
                          required:
                            - key
                            - label
                            - value
                            - short_name_en
                            - buff_sell_price
                            - buff_buy_price
                            - def_index
                            - paint_index
                          x-apifox-orders:
                            - key
                            - label
                            - value
                            - def_index
                            - paint_index
                            - short_name_en
                            - buff_sell_price
                            - buff_buy_price
                        title: 多普勒列表
                      is_collection:
                        type: array
                        items:
                          type: string
                        title: 废弃
                      statistic_list:
                        type: array
                        items:
                          type: object
                          properties:
                            name:
                              type: string
                              title: 饰品类型名称
                            statistic_at:
                              type: string
                              title: 最近统计日期
                            statistic:
                              type: integer
                              title: 存世量
                          required:
                            - name
                            - statistic_at
                            - statistic
                          x-apifox-orders:
                            - name
                            - statistic_at
                            - statistic
                        title: 存世量列表
                        description: 存储同类型的不同饰品的存世量
                      container:
                        type: array
                        items:
                          type: object
                          properties:
                            id:
                              type: integer
                              title: 收藏品id
                            url:
                              type: string
                              title: 收藏品图片
                            name:
                              type: string
                              title: 收藏品名称
                            price:
                              type: number
                              title: 该武器箱当前价格
                            comment:
                              type: string
                              title: 备注
                              description: 包含大行动、稀有度、直售等信息
                            created_at:
                              type: string
                              title: 上线时间
                              description: 指游戏内的更新上线时间
                          required:
                            - id
                            - url
                            - name
                            - price
                            - comment
                            - created_at
                          x-apifox-orders:
                            - id
                            - url
                            - name
                            - price
                            - comment
                            - created_at
                        title: 该饰品所属武器箱/收藏品
                    required:
                      - goods_info
                      - button_list
                      - dpl
                      - is_collection
                      - statistic_list
                      - container
                    x-apifox-orders:
                      - goods_info
                      - button_list
                      - dpl
                      - is_collection
                      - statistic_list
                      - container
                    title: 响应数据
                  msg:
                    type: string
                    title: 响应信息
                required:
                  - code
                  - data
                  - msg
                x-apifox-orders:
                  - code
                  - msg
                  - data
              example:
                code: 200
                data:
                  goods_info:
                    id: 7310
                    turnover_number: 12
                    turnover_avg_price: 1293.65
                    period_at: '2025-10-28T00:00:00'
                    buff_id: 43091
                    yyyp_id: 754
                    name: M9 刺刀（★） | 多普勒 (崭新出厂)
                    market_hash_name: ★ M9 Bayonet | Doppler (Factory New)
                    buff_sell_price: 6750
                    buff_buy_price: 6550
                    buff_sell_num: 1308
                    buff_buy_num: 38
                    yyyp_sell_price: 6669.5
                    yyyp_lease_num: 106
                    yyyp_transfer_price: 7500
                    yyyp_lease_price: 4.14
                    yyyp_long_lease_price: 3.55
                    yyyp_lease_annual: 11.92
                    yyyp_long_lease_annual: 14.05
                    yyyp_sell_num: 1463
                    yyyp_steam_price: 10049
                    yyyp_buy_num: 77
                    yyyp_buy_price: 57000
                    sell_price_rate_1: -2
                    sell_price_rate_7: -2.17
                    sell_price_rate_15: -5.66
                    sell_price_rate_30: -8.13
                    sell_price_rate_90: -4.92
                    sell_price_rate_180: 22.73
                    sell_price_rate_365: -58.33
                    sell_price_1: -138
                    sell_price_7: -150
                    sell_price_15: -405
                    sell_price_30: -597.5
                    sell_price_90: -349.5
                    sell_price_180: 1250
                    sell_price_365: -9450
                    yyyp_sell_price_1: -89.5
                    yyyp_sell_price_7: -180.5
                    yyyp_sell_price_15: -389.5
                    yyyp_sell_price_30: -614
                    yyyp_sell_price_90: -329.5
                    yyyp_sell_price_180: 1669.5
                    yyyp_sell_price_365: -9275.5
                    yyyp_sell_price_rate_1: -1.32
                    yyyp_sell_price_rate_7: -2.63
                    yyyp_sell_price_rate_15: -5.52
                    yyyp_sell_price_rate_30: -8.43
                    yyyp_sell_price_rate_90: -4.71
                    yyyp_sell_price_rate_180: 33.39
                    yyyp_sell_price_rate_365: -58.17
                    r8_sell_price: 10200
                    r8_sell_num: 2
                    steam_sell_price: 10436.62
                    steam_sell_num: 10
                    steam_buy_price: 9300
                    steam_buy_num: 6426
                    steam_buff_buy_conversion: 0.63
                    steam_buff_sell_conversion: 0.65
                    buff_steam_buy_conversion: 0.82
                    buff_steam_sell_conversion: 0.73
                    type_localized_name: 匕首
                    statistic: 29346
                    img: >-
                      https://g.fp.ps.netease.com/market/file/5aa9492eaa49f1631dbfe031hhfyOprC
                    updated_at: '2026-04-23T16:04:06'
                    rank_num: 420
                    rank_num_change: 10
                    def_index: 508
                    paint_index: 0
                    c5_id: '23513'
                    c5_sell_price: 6800
                    c5_sell_num: 262
                    c5_buy_price: 50500
                    c5_buy_num: 19
                    c5_lease_price: 3.93
                    c5_long_lease_price: 3.68
                    igxe_id: '5562'
                    igxe_sell_price: 7000
                    igxe_sell_num: 49
                    igxe_lease_price: 5.8
                    igxe_long_lease_price: 7
                    igxe_lease_num: 23
                    igxe_buy_price: 8510
                    igxe_buy_num: 6
                    eco_id: '102'
                    eco_sku_id: 9bb189a8-1a31-11ee-8008-0c42a1651c04
                    eco_sell_price: 6980
                    eco_sell_num: 266
                    eco_buy_price: 5370
                    eco_buy_num: 7
                    min_float: 0
                    max_float: 0.07
                    rarity_localized_name: 隐秘
                    quality_localized_name: ★
                    exterior_localized_name: 崭新出厂
                    group_hash_name: M9 Bayonet | Doppler
                  button_list:
                    - id: 7310
                      name: 崭新出厂
                      current: true
                      switch: false
                    - id: 7311
                      name: 略有磨损
                      current: false
                      switch: false
                    - id: 7895
                      name: ★ StatTrak™
                      current: false
                      switch: true
                  dpl:
                    - key: 146
                      label: Phase1
                      value: Phase1
                      def_index: 508
                      paint_index: 418
                      short_name_en: M9 Bayonet | Doppler (Phase 1)
                      buff_sell_price: 6799
                      buff_buy_price: 6550
                    - key: 147
                      label: Phase2
                      value: Phase2
                      def_index: 508
                      paint_index: 419
                      short_name_en: M9 Bayonet | Doppler (Phase 2)
                      buff_sell_price: 9249.5
                      buff_buy_price: 8850
                    - key: 148
                      label: Phase3
                      value: Phase3
                      def_index: 508
                      paint_index: 420
                      short_name_en: M9 Bayonet | Doppler (Phase 3)
                      buff_sell_price: 6850
                      buff_buy_price: 6560
                    - key: 149
                      label: Phase4
                      value: Phase4
                      def_index: 508
                      paint_index: 421
                      short_name_en: M9 Bayonet | Doppler (Phase 4)
                      buff_sell_price: 7599
                      buff_buy_price: 6870
                    - key: 150
                      label: 黑珍珠
                      value: Black Pearl
                      def_index: 508
                      paint_index: 417
                      short_name_en: M9 Bayonet | Doppler (Black Pearl)
                      buff_sell_price: 63500
                      buff_buy_price: 54000
                    - key: 151
                      label: 红宝石
                      value: Ruby
                      def_index: 508
                      paint_index: 415
                      short_name_en: M9 Bayonet | Doppler (Ruby)
                      buff_sell_price: 60000
                      buff_buy_price: 49000
                    - key: 152
                      label: 蓝宝石
                      value: Sapphire
                      def_index: 508
                      paint_index: 416
                      short_name_en: M9 Bayonet | Doppler (Sapphire)
                      buff_sell_price: 34498
                      buff_buy_price: 29500
                  is_collection: []
                  statistic_list:
                    - name: Phase1
                      statistic_at: '2025-06-09T15:00:00'
                      statistic: 6762
                    - name: Phase2
                      statistic_at: '2025-06-09T15:00:00'
                      statistic: 6966
                    - name: Phase3
                      statistic_at: '2025-06-09T15:00:00'
                      statistic: 6694
                    - name: Phase4
                      statistic_at: '2025-06-09T15:00:00'
                      statistic: 7168
                    - name: 黑珍珠
                      statistic_at: '2025-06-09T15:00:00'
                      statistic: 190
                    - name: 红宝石
                      statistic_at: '2025-06-09T15:00:00'
                      statistic: 760
                    - name: 蓝宝石
                      statistic_at: '2025-06-09T15:00:00'
                      statistic: 806
                  container:
                    - id: 3
                      url: >-
                        https://g.fp.ps.netease.com/market/file/5a9fd45169b21ae4e2268192XHxJ5hrs
                      name: 幻彩 3 号武器箱
                      price: 24
                      comment: 稀有掉落
                      created_at: '2016-04-27T00:00:00'
                      roi: 57.55
                    - id: 16
                      url: >-
                        https://g.fp.ps.netease.com/market/file/5a9fd45302c9a18f3c9eca349z1RAAZ9
                      name: 幻彩 2 号武器箱
                      price: 27.48
                      comment: 稀有掉落
                      created_at: '2015-04-15T00:00:00'
                      roi: 60.22
                    - id: 14
                      url: >-
                        https://g.fp.ps.netease.com/market/file/5a9fd44f6f0494db8b736a20wr58RkEJ
                      name: 幻彩武器箱
                      price: 36.1
                      comment: 稀有掉落
                      created_at: '2015-01-08T00:00:00'
                      roi: 62.44
                msg: Success
          headers: {}
          x-apifox-name: 成功
          x-apifox-ordering: 0
        '422':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/HTTPValidationError'
          headers: {}
          x-apifox-name: Validation Error
          x-apifox-ordering: 1
      security: []
      x-apifox-folder: 接口详情/饰品详情
      x-apifox-status: released
      x-run-in-apifox: https://app.apifox.com/web/project/4711104/apis/api-187131780-run
components:
  schemas:
    HTTPValidationError:
      title: HTTPValidationError
      type: object
      properties:
        detail:
          title: Detail
          type: array
          items:
            $ref: '#/components/schemas/ValidationError'
      x-apifox-orders:
        - detail
      x-apifox-ignore-properties: []
      x-apifox-folder: ''
    ValidationError:
      title: ValidationError
      required:
        - loc
        - msg
        - type
      type: object
      properties:
        loc:
          title: Location
          type: array
          items:
            type: string
        msg:
          title: Message
          type: string
        type:
          title: Error Type
          type: string
      x-apifox-orders:
        - loc
        - msg
        - type
      x-apifox-ignore-properties: []
      x-apifox-folder: ''
  responses: {}
  securitySchemes: {}
servers:
  - url: https://api.csqaq.com
    description: 正式环境
security: []

```