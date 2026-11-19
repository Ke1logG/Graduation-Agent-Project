package com.kl.agent.tool;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 工具的最小参数契约，供 PlanValidator 做 L1 校验。
 *
 * @param name              工具名
 * @param argumentTypes     允许的参数名及其顶层类型，如 String.class / Number.class / List.class
 * @param requiredArguments 必填参数名
 */
public record ToolSchema(
        String name,
        Map<String, Class<?>> argumentTypes,
        Set<String> requiredArguments
) {

    public ToolSchema {
        name = Objects.requireNonNull(name, "name");
        argumentTypes = Map.copyOf(argumentTypes);
        requiredArguments = Set.copyOf(requiredArguments);

        for (String required : requiredArguments) {
            if (!argumentTypes.containsKey(required)) {
                throw new IllegalArgumentException(
                        "必填参数 " + required + " 不在 argumentTypes 中");
            }
        }
    }
}
