package com.kl.agent.llm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.kl.agent.plan.PlanParser;
import com.kl.agent.plan.PlanSpec;
import com.kl.agent.plan.PlanValidator;
import com.kl.agent.plan.StepSpec;
import com.kl.agent.plan.ValidationResult;
import com.kl.agent.tool.TestToolCatalog;

/**
 * 真实 LLM 冒烟测试：走生产链路
 * {@code LlmPlanner -> LlmClient -> PlanParser -> PlanValidator}。
 *
 * <p>普通 {@code mvn test} 不会执行本测试；需要显式打开：</p>
 *
 * <pre>
 * mvn -Dtest=LlmSmokeTest -Dllm.smoke=true test
 * </pre>
 *
 * <p>原始响应会打印到控制台，并保存到 {@code target/llm-smoke/plan-response.json}。</p>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "llm.smoke", matches = "true")
class LlmSmokeTest {

    private static final String USER_PROMPT =
            "查一下《葬送的芙莉莲》（Sousou no Frieren）的评分，并把结果写入一个文件。";

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private PlanParser planParser;

    @Test
    void returnsValidPlan() throws Exception {
        RecordingLlmClient recording = new RecordingLlmClient(llmClient);
        PlanSpec plan = new LlmPlanner(recording, planParser).plan(USER_PROMPT);

        LlmResponse response = Objects.requireNonNull(
                recording.lastResponse(), "LLM 没有返回响应");
        printAndSave(response);

        assertThat(plan.goal()).isNotBlank();
        assertThat(plan.steps()).isNotEmpty();

        for (StepSpec step : plan.steps()) {
            assertThat(step.id()).isNotBlank();
            assertThat(step.tool()).isNotBlank();
            assertThat(step.args()).isNotNull();
            assertThat(step.dependsOn()).isNotNull();
        }

        ValidationResult validation = new PlanValidator(new TestToolCatalog()).validate(plan);
        assertThat(validation.errors())
                .as("plan validation errors: %s", validation.errors())
                .isEmpty();

        System.out.printf(
                "LLM smoke OK: goal=%s steps=%d model=%s promptTokens=%d completionTokens=%d%n",
                plan.goal(),
                plan.steps().size(),
                response.model(),
                response.promptTokens(),
                response.completionTokens()
        );
    }

    /**
     * 打印并保存模型原始文本，方便人工检查提示词效果和后续录制回放。
     */
    private void printAndSave(LlmResponse response) throws Exception {
        System.out.println("===== LLM raw response =====");
        System.out.println(response.text());
        System.out.println("============================");

        Path output = Path.of("target", "llm-smoke", "plan-response.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, response.text(), StandardCharsets.UTF_8);

        System.out.printf(
                "LLM response saved: model=%s promptTokens=%d completionTokens=%d path=%s%n",
                response.model(),
                response.promptTokens(),
                response.completionTokens(),
                output.toAbsolutePath()
        );
    }

    /**
     * 测试用装饰器：委托真实 LlmClient，同时保留最后一次响应。
     */
    private static final class RecordingLlmClient implements LlmClient {

        private final LlmClient delegate;
        private LlmResponse lastResponse;

        private RecordingLlmClient(LlmClient delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public LlmResponse complete(String systemPrompt, String userPrompt) {
            lastResponse = delegate.complete(systemPrompt, userPrompt);
            return lastResponse;
        }

        private LlmResponse lastResponse() {
            return lastResponse;
        }
    }
}
