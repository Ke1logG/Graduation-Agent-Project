package com.kl.agent.llm;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiLlmClientTest {

    /**
     * 测试1：把模型文本、模型名和 token 用量完整映射出来
     */
    @Test
    void mapsTextModelAndUsage() {
        ChatResponse response = response("hello", "qwen-test", 12, 7);

        LlmResponse result = SpringAiLlmClient.toLlmResponse(response);

        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.model()).isEqualTo("qwen-test");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.completionTokens()).isEqualTo(7);
    }

    /**
     * 测试2：用量缺失时按 0 处理，不抛空指针
     */
    @Test
    void defaultsMissingUsageToZero() {
        ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage("hello"))));

        LlmResponse result = SpringAiLlmClient.toLlmResponse(response);

        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.promptTokens()).isZero();
        assertThat(result.completionTokens()).isZero();
    }

    /**
     * 测试3：空文本视为调用失败
     */
    @Test
    void rejectsEmptyResponseText() {
        ChatResponse response = response("   ", "qwen-test", 1, 1);

        assertThatThrownBy(() -> SpringAiLlmClient.toLlmResponse(response))
                .isInstanceOf(LlmClientException.class)
                .hasMessageContaining("空响应");
    }

    /**
     * 测试4：complete 把 system/user 提示传给 ChatClient
     */
    @Test
    void sendsPromptsThroughChatClient() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(request);
        when(request.system("system prompt")).thenReturn(request);
        when(request.user("user prompt")).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.chatResponse()).thenReturn(response("hello", "qwen-test", 3, 5));

        LlmResponse result = new SpringAiLlmClient(chatClient)
                .complete("system prompt", "user prompt");

        assertThat(result.text()).isEqualTo("hello");
        verify(request).system("system prompt");
        verify(request).user("user prompt");
        verify(call).chatResponse();
    }

    /**
     * 测试5：底层运行时异常统一包装成 LlmClientException
     */
    @Test
    void wrapsRuntimeException() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new IllegalStateException("connection reset"));

        assertThatThrownBy(() -> new SpringAiLlmClient(chatClient)
                .complete("system", "user"))
                .isInstanceOf(LlmClientException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection reset");
    }

    /**
     * 测试6：FakeLlmClient 记录收到的提示词和调用次数
     */
    @Test
    void fakeClientRecordsPrompts() {
        FakeLlmClient client = new FakeLlmClient("fixed response");

        LlmResponse result = client.complete("system", "user");

        assertThat(result.text()).isEqualTo("fixed response");
        assertThat(client.lastSystemPrompt()).isEqualTo("system");
        assertThat(client.lastUserPrompt()).isEqualTo("user");
        assertThat(client.callCount()).isEqualTo(1);
    }

    private ChatResponse response(String text, String model, int promptTokens, int completionTokens) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(model)
                .usage(new DefaultUsage(promptTokens, completionTokens))
                .build();

        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                metadata
        );
    }
}
