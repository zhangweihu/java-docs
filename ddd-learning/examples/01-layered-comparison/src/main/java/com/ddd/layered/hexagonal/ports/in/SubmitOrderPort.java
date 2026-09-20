package com.ddd.layered.hexagonal.ports.in;

import com.ddd.layered.hexagonal.application.SubmitOrderCommand;

/**
 * 入口端口（Inbound Port / Driving Port）。
 *
 * <p>业务核心对外暴露的能力。任何"驱动业务核心"的适配器（REST、CLI、MQ、定时任务）
 * 都通过调用此端口来执行业务用例。
 *
 * <p>这是六边形架构与 DDD 四层的命名差异点：
 * <ul>
 *   <li>DDD：{@code ApplicationService}</li>
 *   <li>Hex：{@code Port}（命名更鲜明地表达"边界"）</li>
 * </ul>
 */
public interface SubmitOrderPort {
    Long submit(SubmitOrderCommand cmd);
}