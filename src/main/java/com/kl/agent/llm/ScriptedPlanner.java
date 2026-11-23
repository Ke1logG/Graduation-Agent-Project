package com.kl.agent.llm;

import com.kl.agent.plan.PlanSpec;

import java.util.Objects;

/**
 * 脚本化规划器：忽略输入目标，始终返回构造时注入的固定计划。
 *
 * <p>用于在接真实 LLM 之前开发和测试执行器、故障处理和评测链路。</p>
 */
public final class ScriptedPlanner implements Planner {

    private final PlanSpec plan;

    public ScriptedPlanner(PlanSpec plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    @Override
    public PlanSpec plan(String goal) {
        return plan;
    }
}
