package com.kl.agent.plan;

import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * 把 LLM / 测试夹具返回的原始文本转换为 {@link PlanSpec}。
 *
 * <p>只负责 L0：去掉 Markdown 围栏和前后说明，截取最外层 JSON，再做反序列化。
 * 结构、工具、依赖图等合法性由 {@link PlanValidator} 检查。</p>
 */
public final class PlanParser {

    private final ObjectMapper objectMapper;

    public PlanParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * 解析原始文本；空文本、非 JSON 或字段类型不匹配时抛出 {@link PlanParseException}。
     */
    public PlanSpec parse(String raw) {
        String json = extractJson(raw);
        try {
            return objectMapper.readValue(json, PlanSpec.class);
        } catch (Exception e) {
            throw new PlanParseException("计划不是合法 JSON 或结构不匹配: " + e.getMessage(), e);
        }
    }

    /**
     * 容忍模型返回 Markdown 围栏或前后说明；只截取最外层 JSON。
     */
    String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PlanParseException("计划文本为空");
        }

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
        if (start < 0 || end <= start) {
            throw new PlanParseException("响应中没有完整的 JSON 对象");
        }

        return text.substring(start, end + 1).trim();
    }
}
