package com.kl.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import com.kl.agent.llm.LlmClient;
import com.kl.agent.llm.LlmPlanner;
import com.kl.agent.llm.SpringAiLlmClient;
import com.kl.agent.plan.PlanParser;

/**
 * LLM 相关 Bean 的装配。
 *
 * <p>这里只负责“怎么连模型”，不设置系统提示词或业务 advisor；
 * 提示词由 {@link com.kl.agent.llm.PlanPromptBuilder} 生成，按次传给 LlmClient。</p>
 */
@Configuration
public class LlmConfiguration {

    /**
     * 构建可复用的 ChatClient；模型、温度、超时等仍由 application.yaml 控制。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * 把 Spring AI 隔离在传输层，规划器只依赖 LlmClient。
     */
    @Bean
    public LlmClient llmClient(ChatClient chatClient) {
        return new SpringAiLlmClient(chatClient);
    }

    /**
     * 复用 Spring Boot 提供的 ObjectMapper 做计划 JSON 解析。
     */
    @Bean
    public PlanParser planParser(ObjectMapper objectMapper) {
        return new PlanParser(objectMapper);
    }

    /**
     * 真实规划器：当前消费方只有冒烟测试，后续由运行服务注入。
     */
    @Bean
    public LlmPlanner llmPlanner(LlmClient llmClient, PlanParser planParser) {
        return new LlmPlanner(llmClient, planParser);
    }
}
