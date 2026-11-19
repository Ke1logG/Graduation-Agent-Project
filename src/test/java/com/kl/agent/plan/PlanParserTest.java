package com.kl.agent.plan;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanParserTest {

    private final PlanParser parser = new PlanParser(new ObjectMapper());

    @Test
    void parsesPlainJson() {
        PlanSpec plan = parser.parse("""
                {
                  "goal": "Calculate 1 + 2",
                  "steps": [
                    {
                      "id": "s1",
                      "tool": "calculator",
                      "args": {"expression": "1 + 2"},
                      "dependsOn": []
                    }
                  ]
                }
                """);

        assertThat(plan.goal()).isEqualTo("Calculate 1 + 2");
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).tool()).isEqualTo("calculator");
    }

    @Test
    void parsesMarkdownFencedJson() {
        PlanSpec plan = parser.parse("""
                ```json
                {"goal": "x", "steps": []}
                ```
                """);

        assertThat(plan.goal()).isEqualTo("x");
        assertThat(plan.steps()).isEmpty();
    }

    @Test
    void parsesJsonWithProsePrefix() {
        PlanSpec plan = parser.parse("""
                Here is the plan:
                {"goal": "x", "steps": []}
                """);

        assertThat(plan.goal()).isEqualTo("x");
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> parser.parse("  "))
                .isInstanceOf(PlanParseException.class);
    }

    @Test
    void rejectsNullText() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(PlanParseException.class);
    }

    @Test
    void rejectsMalformedJson() {
        // 花括号不完整：在 extractJson 阶段就被拒绝，不会走到 Jackson
        assertThatThrownBy(() -> parser.parse("{ \"goal\": \"broken\", \"steps\": ["))
                .isInstanceOf(PlanParseException.class)
                .hasMessageContaining("响应中没有完整的 JSON 对象");
    }

    @Test
    void wrapsJacksonErrorsAsParseException() {
        // 花括号完整、能通过 extractJson；但 steps 元素不是对象，Jackson 反序列化失败，
        // 用来覆盖 PlanParser 里包装 Jackson 异常的 catch 分支
        assertThatThrownBy(() -> parser.parse("{\"goal\": \"x\", \"steps\": [123]}"))
                .isInstanceOf(PlanParseException.class)
                .hasMessageContaining("计划不是合法 JSON 或结构不匹配");
    }
}
