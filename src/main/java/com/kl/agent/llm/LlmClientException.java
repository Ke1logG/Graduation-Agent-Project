package com.kl.agent.llm;

/**
 * 模型调用失败时抛出：网络异常、超时、鉴权失败、空响应等。
 */
public class LlmClientException extends RuntimeException {

    public LlmClientException(String message) {
        super(message);
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
