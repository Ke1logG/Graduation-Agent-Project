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
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{ \"goal\": \"broken\", \"steps\": ["))
                .isInstanceOf(PlanParseException.class);
    }
}
