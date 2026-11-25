package com.kl.agent.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.kl.agent.plan.PlanSpec;
import com.kl.agent.plan.StepSpec;
import com.kl.agent.tool.Tool;
import com.kl.agent.tool.ToolRegistry;

/**
 * 最小顺序执行器。
 *
 * <p>按计划声明顺序执行步骤；某一步失败时立即停止，不重试、不补偿、不持久化。
 * 参数中的 {@code $sN.xxx} 引用暂不解析，留到下一小步。</p>
 */
public final class SequentialExecutor {

    /**
     * 注入工具表
     */
    private final ToolRegistry toolRegistry;

    public SequentialExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
    }

    /**
     * 传入计划，不合法直接返回
     */
    public ExecutionResult execute(PlanSpec plan) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            return ExecutionResult.failure(List.of(), Map.of(), "计划为空或没有步骤");
        }

        Map<String, Object> outputs = new LinkedHashMap<>();
        List<StepResult> stepResults = new ArrayList<>();

        //遍历步骤，校验步骤前置依赖完成，工具存在
        for (StepSpec step : plan.steps()) {
            try {
                checkDependencies(step, outputs);
                Tool tool = toolRegistry.find(step.tool())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "工具不存在: " + step.tool()));

                //拿到参数并调用工具执行
                Map<String, Object> args = step.args() == null ? Map.of() : step.args();
                Object output = tool.execute(args);

                //记录单步计划结果
                outputs.put(step.id(), output);
                stepResults.add(StepResult.success(step.id(), output));
            //发现异常，记录异常信息并整体失败
            } catch (RuntimeException e) {
                String message = e.getMessage() == null
                        ? e.getClass().getSimpleName()
                        : e.getMessage();
                stepResults.add(StepResult.failure(step.id(), message));
                return ExecutionResult.failure(stepResults, outputs, message);
            }
        }

        //返回封装好的整体执行结果
        return ExecutionResult.success(stepResults, outputs);
    }

    /**
     * 顺序执行要求依赖步骤已经成功执行；依赖未完成时直接失败。
     */
    private void checkDependencies(StepSpec step, Map<String, Object> outputs) {
        if (step.dependsOn() == null) {
            return;
        }
        for (String dependency : step.dependsOn()) {
            if (!outputs.containsKey(dependency)) {
                throw new IllegalStateException("依赖步骤未完成: " + dependency);
            }
        }
    }
}
