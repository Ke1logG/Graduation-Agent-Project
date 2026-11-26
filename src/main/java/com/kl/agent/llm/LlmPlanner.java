package com.kl.agent.llm;

import java.util.Objects;

import com.kl.agent.plan.PlanParser;
import com.kl.agent.plan.PlanSpec;

/**
 * 真实 LLM 规划器：把任务目标转换为结构化计划。
 *
 * <p>只负责提示词调用和 L0 解析；计划校验、修复重规划、工具执行都不在这里。</p>
 */
public final class LlmPlanner implements Planner {

    private final LlmClient llmClient;
    private final PlanParser planParser;
    private final String systemPrompt;

    public LlmPlanner(LlmClient llmClient, PlanParser planParser) {
        this(llmClient, planParser, PlanPromptBuilder.systemPrompt());
    }

    /**
     * 允许测试或后续场景替换系统提示词。
     */
    LlmPlanner(LlmClient llmClient, PlanParser planParser, String systemPrompt) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.planParser = Objects.requireNonNull(planParser, "planParser");
        this.systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt");
    }

    /**
     * 调一次模型，把返回文本交给 PlanParser。
     *
     * <p>解析失败原样抛出 PlanParseException，由上层决定是否做一次修复重规划。</p>
     */
    @Override
    public PlanSpec plan(String goal) {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal 不能为空");
        }

        LlmResponse response = llmClient.complete(systemPrompt, goal);
        return planParser.parse(response.text());
    }
}
