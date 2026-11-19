package com.kl.agent.plan;

import com.kl.agent.tool.ToolCatalog;
import com.kl.agent.tool.ToolSchema;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 计划校验器，当前覆盖 L1（工具/参数）和 L2（DAG/引用）。
 *
 * <p>L3 的预算、排序字段白名单和文件路径白名单在执行器阶段补充。</p>
 */
public final class PlanValidator {

    private static final Pattern STEP_REFERENCE =
            Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)\\.");

    private final ToolCatalog toolCatalog;

    public PlanValidator(ToolCatalog toolCatalog) {
        this.toolCatalog = Objects.requireNonNull(toolCatalog, "toolCatalog");
    }

    public ValidationResult validate(PlanSpec plan) {
        List<PlanError> errors = new ArrayList<>();

        if (plan == null) {
            return new ValidationResult(List.of(
                    new PlanError(PlanErrorCode.EMPTY_PLAN, "计划为空")));
        }

        if (isBlank(plan.goal())) {
            add(errors, PlanErrorCode.EMPTY_GOAL, null, "goal 不能为空");
        }

        if (plan.steps() == null || plan.steps().isEmpty()) {
            add(errors, PlanErrorCode.EMPTY_PLAN, null, "steps 不能为空");
            return new ValidationResult(errors);
        }

        Map<String, StepSpec> stepsById = collectSteps(plan.steps(), errors);

        for (StepSpec step : plan.steps()) {
            if (step == null || isBlank(step.id())) {
                continue;
            }
            validateToolAndArgs(step, errors);
            validateDependencies(step, stepsById, errors);
        }

        detectCycle(stepsById, errors);
        validateReferences(stepsById, errors);

        return new ValidationResult(errors);
    }

    /**
     * 建立 stepId -> StepSpec 索引，同时检查空步骤、空 id 和重复 id。
     */
    private Map<String, StepSpec> collectSteps(List<StepSpec> steps, List<PlanError> errors) {
        Map<String, StepSpec> byId = new LinkedHashMap<>();
        for (StepSpec step : steps) {
            if (step == null) {
                add(errors, PlanErrorCode.INVALID_STEP, null, "存在空步骤");
                continue;
            }
            if (isBlank(step.id())) {
                add(errors, PlanErrorCode.INVALID_STEP, null, "步骤 id 不能为空");
                continue;
            }
            if (byId.putIfAbsent(step.id(), step) != null) {
                add(errors, PlanErrorCode.DUPLICATE_STEP_ID, step.id(),
                        "步骤 id 重复: " + step.id());
            }
        }
        return byId;
    }

    /**
     * L1：工具存在、必填参数、未知参数、顶层类型。
     */
    private void validateToolAndArgs(StepSpec step, List<PlanError> errors) {
        if (isBlank(step.tool())) {
            add(errors, PlanErrorCode.UNKNOWN_TOOL, step.id(), "tool 不能为空");
            return;
        }

        Optional<ToolSchema> maybeSchema = toolCatalog.find(step.tool());
        if (maybeSchema.isEmpty()) {
            add(errors, PlanErrorCode.UNKNOWN_TOOL, step.id(),
                    "未注册的工具: " + step.tool());
            return;
        }

        ToolSchema schema = maybeSchema.get();
        Map<String, Object> args = step.args();

        if (args == null) {
            if (!schema.requiredArguments().isEmpty()) {
                add(errors, PlanErrorCode.MISSING_ARG, step.id(), "args 不能为空");
            }
            return;
        }

        for (String required : schema.requiredArguments()) {
            if (!args.containsKey(required) || args.get(required) == null) {
                add(errors, PlanErrorCode.MISSING_ARG, step.id(),
                        "缺少必填参数: " + required);
            }
        }

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String name = entry.getKey();
            Class<?> expected = schema.argumentTypes().get(name);
            if (expected == null) {
                add(errors, PlanErrorCode.UNKNOWN_ARG, step.id(),
                        "未知参数: " + name);
                continue;
            }

            Object value = entry.getValue();
            if (value != null && !expected.isInstance(value)) {
                add(errors, PlanErrorCode.INVALID_ARG_TYPE, step.id(),
                        "参数 " + name + " 类型错误: 期望 "
                                + expected.getSimpleName() + "，实际 "
                                + value.getClass().getSimpleName());
            }
        }
    }

    /**
     * L2：依赖列表类型、依赖存在、自依赖。
     */
    private void validateDependencies(StepSpec step, Map<String, StepSpec> stepsById,
                                      List<PlanError> errors) {
        if (step.dependsOn() == null) {
            add(errors, PlanErrorCode.INVALID_DEPENDENCIES, step.id(),
                    "dependsOn 必须是数组");
            return;
        }

        for (String dependency : step.dependsOn()) {
            if (isBlank(dependency)) {
                add(errors, PlanErrorCode.UNKNOWN_DEPENDENCY, step.id(),
                        "dependsOn 中存在空 id");
            } else if (dependency.equals(step.id())) {
                add(errors, PlanErrorCode.SELF_DEPENDENCY, step.id(),
                        "步骤不能依赖自身: " + dependency);
            } else if (!stepsById.containsKey(dependency)) {
                add(errors, PlanErrorCode.UNKNOWN_DEPENDENCY, step.id(),
                        "依赖的步骤不存在: " + dependency);
            }
        }
    }

    /**
     * L2：DFS 检测依赖环；只报告一次。
     */
    private void detectCycle(Map<String, StepSpec> stepsById, List<PlanError> errors) {
        Map<String, VisitState> states = new HashMap<>();
        for (String id : stepsById.keySet()) {
            if (hasCycle(id, stepsById, states)) {
                add(errors, PlanErrorCode.CYCLE, id, "依赖图存在环");
                return;
            }
        }
    }

    private boolean hasCycle(String id, Map<String, StepSpec> stepsById,
                             Map<String, VisitState> states) {
        VisitState state = states.get(id);
        if (state == VisitState.VISITING) {
            return true;
        }
        if (state == VisitState.VISITED) {
            return false;
        }

        states.put(id, VisitState.VISITING);
        StepSpec step = stepsById.get(id);
        if (step != null && step.dependsOn() != null) {
            for (String dependency : step.dependsOn()) {
                if (dependency != null
                        && stepsById.containsKey(dependency)
                        && hasCycle(dependency, stepsById, states)) {
                    return true;
                }
            }
        }
        states.put(id, VisitState.VISITED);
        return false;
    }

    /**
     * L2：扫描 args 中的 "$sN.xxx" 引用，要求目标存在且是当前步骤的前置。
     */
    private void validateReferences(Map<String, StepSpec> stepsById, List<PlanError> errors) {
        for (StepSpec step : stepsById.values()) {
            if (step.args() == null) {
                continue;
            }

            Set<String> references = new HashSet<>();
            collectReferences(step.args(), references);

            for (String reference : references) {
                if (reference.equals(step.id())) {
                    add(errors, PlanErrorCode.UNKNOWN_REFERENCE, step.id(),
                            "步骤不能引用自身: " + reference);
                } else if (!stepsById.containsKey(reference)) {
                    add(errors, PlanErrorCode.UNKNOWN_REFERENCE, step.id(),
                            "引用不存在的步骤: " + reference);
                } else if (!isAncestor(reference, step.id(), stepsById)) {
                    add(errors, PlanErrorCode.MISSING_DEPENDENCY, step.id(),
                            "引用 " + reference + " 但未在 dependsOn 中声明");
                }
            }
        }
    }

    private void collectReferences(Object value, Set<String> references) {
        if (value instanceof String text) {
            Matcher matcher = STEP_REFERENCE.matcher(text);
            while (matcher.find()) {
                references.add(matcher.group(1));
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                collectReferences(item, references);
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectReferences(item, references);
            }
        } else if (value instanceof Object[] array) {
            for (Object item : array) {
                collectReferences(item, references);
            }
        }
    }

    private boolean isAncestor(String ancestor, String stepId, Map<String, StepSpec> stepsById) {
        StepSpec step = stepsById.get(stepId);
        if (step == null || step.dependsOn() == null) {
            return false;
        }

        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (String dependency : step.dependsOn()) {
            if (dependency != null) {
                pending.add(dependency);
            }
        }

        while (!pending.isEmpty()) {
            String current = pending.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(ancestor)) {
                return true;
            }
            StepSpec currentStep = stepsById.get(current);
            if (currentStep != null && currentStep.dependsOn() != null) {
                for (String dependency : currentStep.dependsOn()) {
                    if (dependency != null) {
                        pending.add(dependency);
                    }
                }
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void add(List<PlanError> errors, PlanErrorCode code,
                            String stepId, String message) {
        errors.add(new PlanError(code, stepId, message));
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
