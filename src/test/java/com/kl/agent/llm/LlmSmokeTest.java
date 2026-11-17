package com.kl.agent.llm;

import com.kl.agent.plan.PlanSpec;
import com.kl.agent.plan.StepSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G1 技术栈冒烟测试：真实调用千问兼容接口，并把响应解析成 {@link PlanSpec}。
 *
 * <p>普通 {@code mvn test} 不会执行本测试；需要显式打开：</p>
 *
 * <pre>
 * mvn -Dtest=LlmSmokeTest -Dllm.smoke=true test
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "llm.smoke", matches = "true")
class LlmSmokeTest {

    private static final String SYSTEM_PROMPT = """
            You are the planning component of a tool-orchestration engine.
            Return exactly ONE JSON object and nothing else. Do not use Markdown.

            JSON shape:
            {
              "goal": "string",
              "steps": [
                {
                  "id": "s1",
                  "tool": "search_anime",
                  "args": {"query": "string"},
                  "dependsOn": []
                }
              ]
            }

            Available tools:
            - search_anime: args {"query": string}
            - writer: args {"path": string, "content": string}

            Rules:
            - Every step must have a unique id.
            - dependsOn contains ids of earlier steps only.
            - Use only the listed tools.
            - To reference a previous step's result, use strings like "$s1.items".
            """;

    private static final String USER_PROMPT =
            "Find the score of Sousou no Frieren and write it to a file.";

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsParseablePlan() throws Exception {
        ChatResponse response = chatClientBuilder.build()
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(USER_PROMPT)
                .call()
                .chatResponse();

        assertThat(response).isNotNull();

        String raw = Objects.requireNonNull(response.getResult())
                .getOutput()
                .getText();

        assertThat(raw).isNotBlank();

        Path output = Path.of("target", "llm-smoke", "plan-smoke-response.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, raw);

        PlanSpec plan = objectMapper.readValue(extractJson(raw), PlanSpec.class);

        assertThat(plan.goal()).isNotBlank();
        assertThat(plan.steps()).isNotEmpty();

        for (StepSpec step : plan.steps()) {
            assertThat(step.id()).isNotBlank();
            assertThat(step.tool()).isNotBlank();
            assertThat(step.args()).isNotNull();
            assertThat(step.dependsOn()).isNotNull();
        }

        Usage usage = response.getMetadata().getUsage();
        System.out.printf(
                "LLM smoke OK: model=%s promptTokens=%s completionTokens=%s raw=%s%n",
                response.getMetadata().getModel(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                output.toAbsolutePath()
        );
    }

    /**
     * 容忍模型偶尔返回 Markdown 围栏或前后说明；只截取最外层 JSON。
     */
    private String extractJson(String raw) {
        String text = raw.trim();

        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd >= 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int closing = text.lastIndexOf("```");
            if (closing >= 0) {
                text = text.substring(0, closing);
            }
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1).trim();
        }
        return text.trim();
    }
}
