package com.kl.agent.llm;

import com.kl.agent.plan.PlanSpec;

/**
 * 规划器抽象：把任务目标转换为结构化计划。
 *
 * <p>实现可以是脚本固定计划、真实 LLM，或后续的录制回放；执行器只依赖本接口。</p>
 */
@FunctionalInterface
public interface Planner {

    PlanSpec plan(String goal);
}
