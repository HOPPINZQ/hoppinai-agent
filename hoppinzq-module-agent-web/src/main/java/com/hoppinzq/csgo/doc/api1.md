# 获取饰品列表信息

## OpenAPI Specification

```yaml
openapi: 3.0.1
info:
  title: ''
  version: 1.0.0
paths:
  /api/v1/info/get_page_list:
    post:
      summary: 获取饰品列表信息
      deprecated: false
      description: 🥇 获取全站所有饰品的列表信息数据（对应网站页面 [CSQAQ-饰品列表](https://csqaq.com/detail) ）
      operationId: ________api_v1_info_get_page_list_post
      tags:
        - 接口详情/ 涨跌/热门排行
        - api
      parameters:
        - name: ApiToken
          in: header
          description: 用户请求令牌（请自行申请）
          required: true
          example: '{{PRO_TOKEN}}'
          schema:
            type: string
            default: '{{PRO_TOKEN}}'
      requestBody:
        content:
          application/json:
            schema:
              type: object
              x-apifox-refs: {}
              x-apifox-orders:
                - page_index
                - page_size
                - search
                - filter
              properties:
                page_index:
                  title: 查询页码
                  minimum: 1
                  type: integer
                page_size:
                  title: 每页的记录数
                  maximum: 500
                  minimum: 1
                  type: integer
                search:
                  title: 模糊搜索
                  type: string
                filter:
                  title: 饰品筛选
                  type: object
                  x-apifox-orders: []
                  properties: {}
                  description: >-
                    filter字段的格式为：

                    ```json

                    {"类别": ["unusual"], "磨损": ["崭新出厂"], "类型": ["不限_匕首"]}

                    ```

                    key列表有

                    - 类型

                    - 品质

                    - 类别

                    - 磨损


                    每个key可以填写的对应值分别为


                    -  **类型**

                    不限_匕首、蝴蝶刀、M9
                    刺刀、爪子刀、廓尔喀刀、骷髅匕首、刺刀、锯齿爪刀、流浪者匕首、折叠刀、短剑、海豹短刀、熊刀、猎杀者匕首、系绳匕首、求生匕首、弯刀、暗影双匕、鲍伊猎刀、穿肠刀、折刀、不限_手套、运动手套、专业手套、摩托手套、驾驶手套、手部束带、狂牙手套、九头蛇手套、血猎手套、不限_步枪、AK-47、AWP、M4A1
                    消音版、M4A4、AUG、SG 553、法玛斯、加利尔 AR、SSG
                    08、SCAR-20、G3SG1、不限_手枪、沙漠之鹰、USP 消音版、格洛克 18
                    型、P2000、P250、FN57、R8 左轮手枪、Tec-9、双持贝瑞塔、CZ75
                    自动手枪、电击枪、不限_微型冲锋枪、MP9、MAC-10、UMP-45、P90、MP7、PP-野牛、MP5-SD、XM1014、MAG-7、截短霰弹枪、新星、M249、内格夫、不限_武器箱、音乐盒、印花、工具、收藏品、布章、通行证、不限_探员、反恐精英、恐怖分子

                    -  **品质**

                    违禁、隐秘、保密、受限、军规级、工业级、消费级、非凡、卓越、奇异、高级、普通级、探员_高级、探员_卓越、探员_非凡、大师

                    -  **类别**

                    使用中英文传参均可 ↓

                    普通（normal）、纪念品（tournament）、StatTrak™（strange）、★（unusual）、★
                    StatTrak™（unusual_strange）

                    -  **磨损**

                    崭新出厂、略有磨损、久经沙场、破损不堪、战痕累累、无涂装
              required:
                - page_index
                - page_size
            example:
              page_index: 1
              page_size: 18
              search: 蝴蝶
              filter:
                类别:
                  - unusual
                磨损:
                  - 崭新出厂
                类型:
                  - 不限_匕首
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
                  msg:
                    type: string
                    title: 响应信息
                  data:
                    type: object
                    properties:
                      current_page:
                        type: integer
                        title: 当前页码
                      data:
                        type: array
                        items:
                          type: object
                          properties:
                            id:
                              type: integer
                              title: 饰品good_id
                            name:
                              type: string
                              title: 饰品名称
                            exterior_localized_name:
                              type: string
                              title: 饰品磨损
                              description: 崭新出厂、略有磨损等
                            rarity_localized_name:
                              type: string
                              title: 饰品品质
                              description: 普通级、受限、非凡、隐秘等
                            img:
                              type: string
                              title: 饰品图片
                            yyyp_sell_price:
                              type: number
                              title: 悠悠有品在售价格
                            yyyp_sell_num:
                              type: number
                              title: 悠悠有品在售数量
                          required:
                            - id
                            - name
                            - exterior_localized_name
                            - rarity_localized_name
                            - img
                            - yyyp_sell_price
                            - yyyp_sell_num
                          x-apifox-orders:
                            - id
                            - name
                            - exterior_localized_name
                            - rarity_localized_name
                            - img
                            - yyyp_sell_price
                            - yyyp_sell_num
                    required:
                      - current_page
                      - data
                    x-apifox-orders:
                      - current_page
                      - data
                    title: 响应数据
                required:
                  - code
                  - msg
                  - data
                x-apifox-orders:
                  - code
                  - msg
                  - data
              example:
                code: 200
                msg: Success
                data:
                  current_page: 1
                  data:
                    - id: 6798
                      name: 蝴蝶刀（★） | 蓝钢 (崭新出厂)
                      exterior_localized_name: 崭新出厂
                      rarity_localized_name: 隐秘
                      img: >-
                        https://g.fp.ps.netease.com/market/file/5a9fc29d7f9d2aa6a4e126e1pw0YYylI
                      yyyp_sell_price: 14300
                      yyyp_sell_num: 16
                    - id: 6803
                      name: 蝴蝶刀（★） | 北方森林 (崭新出厂)
                      exterior_localized_name: 崭新出厂
                      rarity_localized_name: 隐秘
                      img: >-
                        https://g.fp.ps.netease.com/market/file/5a9fc2b9a7501417d3ce218c1sYNxouz
                      yyyp_sell_price: 9998.5
                      yyyp_sell_num: 2
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
      x-apifox-folder: 接口详情/ 涨跌/热门排行
      x-apifox-status: released
      x-run-in-apifox: https://app.apifox.com/web/project/4711104/apis/api-187131775-run
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


### 示例请求

```json
{
    "page_index": 1,
    "page_size": 1,
    "filter": {
        "类别": [
            "普通"
        ],
        "磨损": [
            "崭新出厂"
        ],
        "类型": [
            "AK-47"
        ]
    }
}
```

### 返回值

```json
{
    "code": 200,
    "msg": "Success",
    "data": {
        "current_page": 1,
        "data": [
            {
                "id": 38,
                "name": "AK-47 | 深海复仇 (崭新出厂)",
                "exterior_localized_name": "崭新出厂",
                "rarity_localized_name": "隐秘",
                "img": "https://g.fp.ps.netease.com/market/file/5aa0c34c46072b9257c809bcGstzDgll",
                "yyyp_sell_price": 989.0,
                "yyyp_sell_num": 384
            }
        ]
    }
}
```

### 没有数据了
```json
{
    "code": 200,
    "msg": "Success",
    "data": {
        "current_page": 1,
        "data": []
    }
}
```