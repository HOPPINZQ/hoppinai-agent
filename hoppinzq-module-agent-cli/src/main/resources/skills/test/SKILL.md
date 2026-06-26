---
name: test
description: 测试最佳实践和模式
tags: test,testing,quality
---

# 测试最佳实践技能

## 目标
指导用户编写有效的测试并遵循测试最佳实践。

## 测试金字塔

```
        /\
       /  \      端到端测试（少量）
      /____\
     /      \    集成测试（适量）
    /________\
   /          \  单元测试（大量）
  /____________\
```

## 单元测试

### 原则
- **快速**：测试应该快速运行
- **隔离**：每个测试应该独立
- **可重复**：测试应该产生一致的结果
- **自验证**：测试应该有明确的通过/失败结果

### 结构（AAA模式）
1. **准备（Arrange）**：设置测试数据和条件
2. **执行（Act）**：执行被测代码
3. **断言（Assert）**：验证预期结果

### 示例
```java
@Test
public void calculateTotalPrice_withDiscount_appliesDiscountCorrectly() {
    // 准备
    Cart cart = new Cart();
    cart.addItem(new Item("商品", 100.0, 2));
    cart.setDiscount(0.1); // 10%折扣

    // 执行
    double total = cart.calculateTotal();

    // 断言
    assertEquals(180.0, total, 0.01);
}
```

## 测试命名

### 约定
`方法名_状态_预期结果`

### 示例
- ✅ `calculateTotal_withEmptyCart_returnsZero`
- ✅ `login_withValidCredentials_returnsSuccess`
- ✅ `transfer_withInsufficientFunds_throwsException`
- ❌ `test1`
- ❌ `calculateTotalTest`

## 最佳实践

### 应该做
- 每个测试只测试一件事
- 使用描述性的测试名称
- 遵循AAA模式
- 模拟外部依赖
- 测试边界情况和错误条件
- 保持测试简单易读
- 使用断言库

### 不应该做
- 在一个测试中测试多件事
- 编写过于复杂的测试
- 依赖测试执行顺序
- 使用没有意义的硬编码值
- 忽略失败的测试
- 测试第三方库

## 常见模式

### 参数化测试
```java
@ParameterizedTest
@ValueSource(strings = {"racecar", "madam", "level"})
void isPalindrome_withValidPalindromes_returnsTrue(String word) {
    assertTrue(PalindromeChecker.isPalindrome(word));
}
```

### 异常测试
```java
@Test
void withdraw_withNegativeAmount_throwsIllegalArgumentException() {
    Account account = new Account(100.0);

    assertThrows(IllegalArgumentException.class, () -> {
        account.withdraw(-50.0);
    });
}
```

### 模拟
```java
@Test
void processOrder_withValidPayment_callsPaymentService() {
    // 准备
    PaymentService mockPaymentService = mock(PaymentService.class);
    OrderService orderService = new OrderService(mockPaymentService);
    Order order = new Order(100.0);

    // 执行
    orderService.processOrder(order);

    // 验证
    verify(mockPaymentService).charge(100.0);
}
```

## 测试覆盖率

### 指标
- **行覆盖率**：已执行代码行的百分比
- **分支覆盖率**：已执行代码分支的百分比
- **路径覆盖率**：已测试执行路径的百分比

### 目标
- 单元测试：目标80%以上的行覆盖率
- 集成测试：覆盖关键用户流程
- 端到端测试：覆盖关键业务场景

### 覆盖率工具
- Java：JaCoCo
- Python：pytest-cov
- JavaScript：Istanbul/nyc

## 集成测试

### 目标
- 测试组件之间的交互
- 验证数据库操作
- 测试API端点

### 示例
```java
@SpringBootTest
@AutoConfigureMockMvc
public class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void createUser_withValidData_returnsCreated() throws Exception {
        String jsonUser = "{\"name\":\"张三\",\"email\":\"zhangsan@example.com\"}";

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonUser))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("张三"));
    }
}
```

## 提示
- 与代码同时编写测试（测试驱动开发TDD）
- 保持测试快速且专注
- 使用测试固件和构建器
- 定期审查和重构测试
- 将测试代码视为生产代码
