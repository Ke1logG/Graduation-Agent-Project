package com.kl.agent.llm;

import com.kl.agent.plan.PlanParser;
import com.kl.agent.plan.PlanSpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptedPlannerTest {

    private final PlanParser parser = new PlanParser(new ObjectMapper());

    @Test
    void returnsTheInjectedPlanForAnyGoal() {
        PlanSpec fixedPlan = parser.parse(read("valid/two-step.json"));
        ScriptedPlanner planner = new ScriptedPlanner(fixedPlan);

        assertThat(planner.plan("find an anime")).isSameAs(fixedPlan);
        assertThat(planner.plan("another goal")).isSameAs(fixedPlan);
    }

    @Test
    void rejectsNullPlan() {
        assertThatThrownBy(() -> new ScriptedPlanner(null))
                .isInstanceOf(NullPointerException.class);
    }

    private String read(String fixture) {
        String path = "/plans/" + fixture;
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("resource: %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }
}
