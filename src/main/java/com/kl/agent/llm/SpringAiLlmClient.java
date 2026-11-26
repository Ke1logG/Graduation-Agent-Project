package com.kl.agent.llm;

import java.util.Objects;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 基于 Spring AI {@link ChatClient} 的薄适配层。
 *
 * <p>把 Spring AI 的响应转成项目自己的 {@link LlmResponse}，
 * 让规划器不直接依赖 Spring AI，也方便以后换成 HttpClient。</p>
 */
public final class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;

    public SpringAiLlmClient(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
    }

    /**
     * 调用一次模型；不解析计划，只返回原始文本和 token 用量。
     */
    @Override
    public LlmResponse complete(String systemPrompt, String userPrompt) {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");

        try {
            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse();

            return toLlmResponse(response);
        } catch (LlmClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LlmClientException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把 Spring AI 响应映射成 LlmResponse；空响应直接视为调用失败。
     */
    static LlmResponse toLlmResponse(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new LlmClientException("LLM 返回空响应");
        }

        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new LlmClientException("LLM 返回空响应");
        }

        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();

        //封装为自己的相应后返回
        return new LlmResponse(
                text,
                metadata == null ? null : metadata.getModel(),
                tokenCount(usage == null ? null : usage.getPromptTokens()),
                tokenCount(usage == null ? null : usage.getCompletionTokens())
        );
    }

    /**
     * 用量可能缺失，统一按 0 处理，避免上层到处判空。
     */
    private static int tokenCount(Integer tokens) {
        return tokens == null ? 0 : tokens;
    }
}
