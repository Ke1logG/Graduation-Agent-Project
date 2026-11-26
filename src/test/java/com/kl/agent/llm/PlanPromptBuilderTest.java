package com.kl.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PlanPromptBuilderTest {

    /**
     * 测试1：系统提示词必须包含计划结构、工具契约和引用语法
     */
    @Test
    void containsPlanContractAndTools() {
        String prompt = PlanPromptBuilder.systemPrompt();

        assertThat(prompt)
                .contains("\"goal\"")
                .contains("\"steps\"")
                .contains("\"dependsOn\"")
                .contains("search_anime")
                .contains("writer")
                .contains("$s1.items")
                .contains("runs/");
    }

    /**
     * 测试2：明确要求模型只输出 JSON，避免解析器处理自然语言
     */
    @Test
    void requiresJsonOnlyOutput() {
        String prompt = PlanPromptBuilder.systemPrompt();

        assertThat(prompt)
                .contains("只返回一个 JSON 对象")
                .contains("不要使用 Markdown");
    }
}
