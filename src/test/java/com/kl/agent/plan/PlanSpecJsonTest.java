package com.kl.agent.plan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlanSpecJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readsTwoStepPlanFromJson() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/plans/valid-two-step.json")) {
            assertThat(in).isNotNull();

            PlanSpec plan = objectMapper.readValue(in, PlanSpec.class);

            assertThat(plan.goal())
                    .isEqualTo("Find the score of Sousou no Frieren and write it to a file");
            assertThat(plan.steps()).hasSize(2);
            assertThat(plan.steps())
                    .extracting(StepSpec::tool)
                    .containsExactly("search_anime", "writer");

            StepSpec first = plan.steps().get(0);
            assertThat(first.id()).isEqualTo("s1");
            assertThat(first.args()).containsEntry("query", "Sousou no Frieren");
            assertThat(first.dependsOn()).isEmpty();

            StepSpec second = plan.steps().get(1);
            assertThat(second.id()).isEqualTo("s2");
            assertThat(second.args())
                    .containsEntry("path", "runs/${runId}/frieren.json")
                    .containsEntry("content", "$s1.items");
            assertThat(second.dependsOn()).containsExactly("s1");
        }
    }

    @Test
    void serializesPlanBackToJson() throws Exception {
        PlanSpec plan = new PlanSpec(
                "Calculate 1 + 2",
                List.of(new StepSpec(
                        "s1",
                        "calculator",
                        Map.of("expression", "1 + 2"),
                        List.of()
                ))
        );

        String json = objectMapper.writeValueAsString(plan);

        assertThat(json)
                .contains("\"goal\"")
                .contains("\"steps\"")
                .contains("\"calculator\"")
                .contains("\"expression\"");
    }
}
