package com.kl.agent.plan;

import java.util.List;

/**
 * LLM 输出的完整计划。
 *
 * <p>这是引擎内部的数据契约：计划只描述要调用哪些工具、以什么顺序、传什么参数，
 * 不包含任何可直接执行的代码。</p>
 */
public record PlanSpec(
        String goal,
        List<StepSpec> steps
) {
}
