package com.kl.agent.engine;

/**
 * 单个步骤的执行结果。
 *
 * @param stepId       步骤 id
 * @param success      是否成功
 * @param output       工具输出；失败时为 null
 * @param errorMessage 失败说明；成功时为 null
 */
public record StepResult(
        String stepId,
        boolean success,
        Object output,
        String errorMessage
) {

    public static StepResult success(String stepId, Object output) {
        return new StepResult(stepId, true, output, null);
    }

    public static StepResult failure(String stepId, String errorMessage) {
        return new StepResult(stepId, false, null, errorMessage);
    }
}
