package com.kl.agent.llm;

import java.util.Objects;

/**
 * 测试用 LLM 客户端：固定返回构造时注入的响应，并记录收到的提示词。
 */
public final class FakeLlmClient implements LlmClient {

    //提前构造好的固定响应
    private final LlmResponse response;

    //最后一次的系统提示词
    private String lastSystemPrompt;

    //最后一次的用户提示词
    private String lastUserPrompt;

    //调用次数
    private int callCount;

    //构造方法1：传文本，构造默认响应
    public FakeLlmClient(String text) {
        this(new LlmResponse(text, "fake-model", 0, 0));
    }

    //构造方法2：传标准响应，构造标准响应
    public FakeLlmClient(LlmResponse response) {
        this.response = Objects.requireNonNull(response, "response");
    }

    //执行阶段
    @Override
    public LlmResponse complete(String systemPrompt, String userPrompt) {
        callCount++;
        lastSystemPrompt = systemPrompt;
        lastUserPrompt = userPrompt;
        return response;
    }

    /**
    * 类get方法，方便断言
    */
    public String lastSystemPrompt() {
        return lastSystemPrompt;
    }

    public String lastUserPrompt() {
        return lastUserPrompt;
    }

    public int callCount() {
        return callCount;
    }
}
