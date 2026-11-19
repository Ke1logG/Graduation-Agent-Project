package com.kl.agent.plan;

/**
 * 单条计划校验错误。
 *
 * @param code    错误码
 * @param stepId  相关步骤 id；与具体步骤无关时可为 null
 * @param message 可读说明
 */
public record PlanError(PlanErrorCode code, String stepId, String message) {

    public PlanError(PlanErrorCode code, String message) {
        this(code, null, message);
    }
}
