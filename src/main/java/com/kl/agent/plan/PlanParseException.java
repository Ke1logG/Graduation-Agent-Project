package com.kl.agent.plan;

/**
 * 计划文本无法解析为 {@link PlanSpec} 时抛出。
 */
public class PlanParseException extends RuntimeException {

    public PlanParseException(String message) {
        super(message);
    }

    public PlanParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
