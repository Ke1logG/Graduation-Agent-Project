package com.kl.agent.llm;

import com.kl.agent.plan.PlanParser;
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
            你是工具编排引擎中的规划组件。
            只返回一个 JSON 对象，不要输出任何其他内容，不要使用 Markdown。

            JSON 结构（键名必须保持英文）：
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

            可用工具（工具名必须保持英文）：
            - search_anime: args {"query": string}
            - writer: args {"path": string, "content": string}

            规则：
            - 每个步骤的 id 必须唯一。
            - dependsOn 只能包含前面步骤的 id。
            - 只能使用上面列出的工具。
            - 引用前面步骤的结果时，使用 "$s1.items" 这种字符串。
            """;

    private static final String USER_PROMPT =
            "查一下《葬送的芙莉莲》（Sousou no Frieren）的评分，并把结果写入一个文件。";

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

        PlanSpec plan = new PlanParser(objectMapper).parse(raw);

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
}
