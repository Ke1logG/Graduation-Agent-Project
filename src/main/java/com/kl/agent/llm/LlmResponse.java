package com.kl.agent.llm;

import java.util.Objects;

/**
 * 一次模型调用的结果。
 *
 * @param text             模型返回的原始文本
 * @param model            实际使用的模型名
 * @param promptTokens     输入 token 数，缺失时为 0
 * @param completionTokens 输出 token 数，缺失时为 0
 */
public record LlmResponse(
        String text,
        String model,
        int promptTokens,
        int completionTokens
) {

    public LlmResponse {
        text = Objects.requireNonNull(text, "text");
    }
}
