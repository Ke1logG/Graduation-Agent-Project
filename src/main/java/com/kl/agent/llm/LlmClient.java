package com.kl.agent.llm;

/**
 * 大模型调用抽象：一次请求对应一次模型调用。
 *
 * <p>只负责把 system/user 提示发给模型并取回文本和用量；
 * 计划解析、校验和工具执行都不在这一层。</p>
 */
@FunctionalInterface
public interface LlmClient {

    /**
     * 调用模型并返回原始文本及 token 用量。
     *
     * @throws LlmClientException 网络、超时、鉴权失败或空响应
     */
    LlmResponse complete(String systemPrompt, String userPrompt);
}
