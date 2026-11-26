package com.kl.agent.llm;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import com.kl.agent.plan.PlanParseException;
import com.kl.agent.plan.PlanParser;
import com.kl.agent.plan.PlanSpec;

class LlmPlannerTest {

    private final PlanParser parser = new PlanParser(new ObjectMapper());

    /**
     * 测试1：模型返回的 JSON 文本能转换成 PlanSpec
     */
    @Test
    void plansFromLlmText() {
        FakeLlmClient client = new FakeLlmClient(read("recordings/g1-plan-response.json"));
        LlmPlanner planner = new LlmPlanner(client, parser);

        PlanSpec plan = planner.plan("Find the score of Sousou no Frieren");

        assertThat(plan.goal()).isNotBlank();
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0).id()).isEqualTo("s1");
        assertThat(plan.steps().get(1).id()).isEqualTo("s2");
    }

    /**
     * 测试2：规划器把系统提示词和原始 goal 一起交给 LlmClient
     */
    @Test
    void sendsGoalAndToolContract() {
        FakeLlmClient client = new FakeLlmClient(read("recordings/g1-plan-response.json"));
        LlmPlanner planner = new LlmPlanner(client, parser);
        String goal = "找一下《葬送的芙莉莲》的评分";

        planner.plan(goal);

        assertThat(client.callCount()).isEqualTo(1);
        assertThat(client.lastUserPrompt()).isEqualTo(goal);
        assertThat(client.lastSystemPrompt())
                .contains("search_anime")
                .contains("writer")
                .contains("$s1.items");
    }

    /**
     * 测试3：空目标直接拒绝，不浪费一次模型调用
     */
    @Test
    void rejectsBlankGoalWithoutCallingClient() {
        FakeLlmClient client = new FakeLlmClient(read("recordings/g1-plan-response.json"));
        LlmPlanner planner = new LlmPlanner(client, parser);

        assertThatThrownBy(() -> planner.plan("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("goal");

        assertThat(client.callCount()).isZero();
    }

    /**
     * 测试4：模型返回畸形文本时，解析异常原样向上抛出
     */
    @Test
    void propagatesMalformedResponse() {
        FakeLlmClient client = new FakeLlmClient("这不是一个 JSON 计划");
        LlmPlanner planner = new LlmPlanner(client, parser);

        assertThatThrownBy(() -> planner.plan("find an anime"))
                .isInstanceOf(PlanParseException.class);
    }

    private String read(String fixture) {
        String path = "/llm/" + fixture;
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("resource: %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }
}
