package com.kl.agent.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次计划执行的汇总结果。
 *
 * @param success      所有步骤是否都成功
 * @param steps        已执行步骤的结果，按执行顺序
 * @param outputs      stepId -> 工具输出
 * @param errorMessage 整体失败说明；成功时为 null
 */
public record ExecutionResult(
        boolean success,
        List<StepResult> steps,
        Map<String, Object> outputs,
        String errorMessage
) {

    public ExecutionResult {
        steps = List.copyOf(steps);
        outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
    }

    public static ExecutionResult failure(List<StepResult> steps,
                                          Map<String, Object> outputs,
                                          String errorMessage) {
        return new ExecutionResult(false, steps, outputs, errorMessage);
    }

    public static ExecutionResult success(List<StepResult> steps,
                                          Map<String, Object> outputs) {
        return new ExecutionResult(true, steps, outputs, null);
    }
}
