package com.kl.agent.plan;

/**
 * 计划校验错误码。L0 的 JSON 解析失败由 {@link PlanParseException} 表示。
 */
public enum PlanErrorCode {
    EMPTY_GOAL,
    EMPTY_PLAN,
    INVALID_STEP,
    UNKNOWN_TOOL,
    MISSING_ARG,
    UNKNOWN_ARG,
    INVALID_ARG_TYPE,
    INVALID_DEPENDENCIES,
    DUPLICATE_STEP_ID,
    UNKNOWN_DEPENDENCY,
    SELF_DEPENDENCY,
    CYCLE,
    UNKNOWN_REFERENCE,
    MISSING_DEPENDENCY
}
