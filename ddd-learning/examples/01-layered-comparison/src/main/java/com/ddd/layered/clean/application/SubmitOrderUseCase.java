package com.ddd.layered.clean.application;

import com.ddd.layered.clean.application.command.SubmitOrderCommand;

import java.util.List;

/**
 * 整洁架构风格——提交订单用例接口。
 *
 * <p>UseCase 是整洁架构的关键概念：一个用例 = 一个接口 = 一个实现 = 一个事务边界。
 * <p>对比 DDD 四层的 {@code DddOrderAppService}：
 * <ul>
 *   <li>DDD：应用层直接定义 Service 类（concrete class）</li>
 *   <li>Clean：应用层定义 UseCase 接口，外部（Controller）只依赖接口</li>
 * </ul>
 */
public interface SubmitOrderUseCase {
    /** 提交订单，返回新订单 ID。 */
    Long execute(SubmitOrderCommand cmd);

    /** 一个接口承载多个用例也是整洁的常见写法。 */
    interface FindOrderUseCase {
        OrderView findById(Long id);
    }

    /** 读视图 DTO（UseCase 边界返回类型）。 */
    record OrderView(Long orderId, String status, java.math.BigDecimal total) {}
}