package com.kl.agent.plan;

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

import com.kl.agent.tool.ToolCatalog;
import com.kl.agent.tool.ToolSchema;

/**
 * 计划校验器：L1（工具/参数）、L2（DAG/引用）、L3（执行前策略）。
 *
 * <p>L3 当前覆盖步骤上限、文件路径白名单和排序字段白名单；token/金额预算在接真实 LLM 时再补。</p>
 */
public final class PlanValidator {

    private static final Pattern STEP_REFERENCE =
            Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)\\.");

    /**
     * 工具表依赖注入
     */
    private final ToolCatalog toolCatalog;

    /**
     * 执行前策略：步骤上限、路径前缀、排序字段白名单。
     */
    private final ValidationPolicy policy;

    //引用默认模板配置
    public PlanValidator(ToolCatalog toolCatalog) {
        this(toolCatalog, ValidationPolicy.defaults());
    }

    public PlanValidator(ToolCatalog toolCatalog, ValidationPolicy policy) {
        this.toolCatalog = Objects.requireNonNull(toolCatalog, "toolCatalog");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * 计划校验-计划非空，计划目标不为空，计划步骤不为空
     * 其中计划为空，步骤列表为空直接返回，因为后续的校验需要，没有的话根本校验不了
     */
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

        // L3：先检查整体规模，避免把超长计划送入后续校验和执行
        if (plan.steps().size() > policy.maxSteps()) {
            add(errors, PlanErrorCode.TOO_MANY_STEPS, null,
                    "步骤数超过上限: " + plan.steps().size() + " > " + policy.maxSteps());
        }

        /**
         * 整体的校验链条：基础-L1-L2-L3，组合调用类内部的校验方法完成完整校验
         */

        //调用方法拿到步骤映射哈希表
        Map<String, StepSpec> stepsById = collectSteps(plan.steps(), errors);

        //遍历每一个步骤，进行工具，参数，依赖及L3额外政策的详细校验，如果遇到空步骤或空步骤id就跳过，防止空指针异常
        for (StepSpec step : plan.steps()) {
            if (step == null || isBlank(step.id())) {
                continue;
            }
            validateToolAndArgs(step, errors);
            validateDependencies(step, stepsById, errors);
            validateExecutionPolicy(step, errors);
        }

        //进行环校验和上游结果依赖校验
        detectCycle(stepsById, errors);
        validateReferences(stepsById, errors);

        //返回错误的结果集，一次性返回给大模型让他全部修改，避免一次只改一个错误
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
            //如果id不重复，就放入表中，如果重复了，就加入一条错误
            if (byId.putIfAbsent(step.id(), step) != null) {
                add(errors, PlanErrorCode.DUPLICATE_STEP_ID, step.id(),
                        "步骤 id 重复: " + step.id());
            }
        }
        return byId;
    }

    /**
     * L1：工具存在、必填参数、未知参数、顶层类型校验。
     */
    private void validateToolAndArgs(StepSpec step, List<PlanError> errors) {

        //校验工具不为空
        if (isBlank(step.tool())) {
            add(errors, PlanErrorCode.UNKNOWN_TOOL, step.id(), "tool 不能为空");
            return;
        }

        //校验工具表中存在需要的工具，没找到工具就提前返回（Optional类型表示可能返回值，也可能返回null）
        Optional<ToolSchema> maybeSchema = toolCatalog.find(step.tool());
        if (maybeSchema.isEmpty()) {
            add(errors, PlanErrorCode.UNKNOWN_TOOL, step.id(),
                    "未注册的工具: " + step.tool());
            return;
        }

        //取出工具说明和参数
        ToolSchema schema = maybeSchema.get();
        Map<String, Object> args = step.args();

        //如果说明书有必填参数且参数表为空，报错并直接返回
        if (args == null) {
            if (!schema.requiredArguments().isEmpty()) {
                add(errors, PlanErrorCode.MISSING_ARG, step.id(), "args 不能为空");
            }
            return;
        }

        //遍历必填参数，如果参数表里没有或者有但是值为null，添加错误
        for (String required : schema.requiredArguments()) {
            if (!args.containsKey(required) || args.get(required) == null) {
                add(errors, PlanErrorCode.MISSING_ARG, step.id(),
                        "缺少必填参数: " + required);
            }
        }

        //遍历参数，取出名字后去规则表里查，如果查不到表示是未知参数，就加入错误表并跳过这个参数
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String name = entry.getKey();
            Class<?> expected = schema.argumentTypes().get(name);
            if (expected == null) {
                add(errors, PlanErrorCode.UNKNOWN_ARG, step.id(),
                        "未知参数: " + name);
                continue;
            }

            //如果参数非空且参数类型不匹配，就加入错误集
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
        //校验依赖表非空
        if (step.dependsOn() == null) {
            add(errors, PlanErrorCode.INVALID_DEPENDENCIES, step.id(),
                    "dependsOn 必须是数组");
            return;
        }

        //校验每个依赖没有空id，不依赖自身，依赖的步骤合法存在
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
     * L3：文件路径白名单和排序字段白名单。
     */
    private void validateExecutionPolicy(StepSpec step, List<PlanError> errors) {
        //检查参数，如果没有参数直接返回
        Map<String, Object> args = step.args();
        if (args == null) {
            return;
        }

        //校验path参数类型为字符串，如果是字符串，交给validatePath方法进一步检查（之前L1校验过参数类型，所以不是字符串这里也不报错，避免重复）
        Object pathValue = args.get("path");
        if (pathValue instanceof String path) {
            validatePath(step, path, errors);
        }

        //校验rank_anime工具的sortBy字段，如果是字符串且白名单里不包含，就加入错误集
        if ("rank_anime".equals(step.tool())) {
            Object sortValue = args.get("sortBy");
            if (sortValue instanceof String sortBy
                    && !policy.allowedSortFields().contains(sortBy)) {
                add(errors, PlanErrorCode.INVALID_SORT_FIELD, step.id(),
                        "不允许的排序字段: " + sortBy);
            }
        }
    }

    /**
     * 校验路径必须是相对路径、不能包含 ..、且落在允许的前缀（白名单）内。
     */
    private void validatePath(StepSpec step, String path, List<PlanError> errors) {
        String normalized = path.trim().replace('\\', '/');

        if (normalized.isEmpty()
                || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")) {
            add(errors, PlanErrorCode.UNSAFE_PATH, step.id(),
                    "路径必须是白名单内的相对路径: " + path);
            return;
        }

        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                add(errors, PlanErrorCode.UNSAFE_PATH, step.id(),
                        "路径不能包含 ..: " + path);
                return;
            }
        }

        boolean allowed = policy.allowedPathPrefixes().stream()
                .anyMatch(normalized::startsWith);
        if (!allowed) {
            add(errors, PlanErrorCode.UNSAFE_PATH, step.id(),
                    "路径不在允许范围内: " + path);
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
        //如果访问到的时候状态是VISITING，代表有环
        if (state == VisitState.VISITING) {
            return true;
        }
        if (state == VisitState.VISITED) {
            return false;
        }

        //标记当前id为VISITING，递归寻找它的依赖步骤
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
        //遍历步骤，跳过空参数
        for (StepSpec step : stepsById.values()) {
            if (step.args() == null) {
                continue;
            }

            //调用方法提取引用集合
            Set<String> references = new HashSet<>();
            collectReferences(step.args(), references);

            //遍历引用，校验不能引用自身，不能引用不存在步骤，不能未在前置依赖中声明
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
    /**
     * 剥洋葱式递归提取引用集合（引用有可能是用不同类型的集合装着，全部提取出来放到字符串集合并去重）
     */
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
    /**
     * 顺着依赖链追根溯源，确认被引用的步骤确实排在前面且已声明依赖
     */
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
