package com.kl.agent.plan;

import java.util.List;
import java.util.Map;

/**
 * 计划中的单个步骤。
 *
 * @param id        步骤标识，如 s1
 * @param tool      工具名称
 * @param args      工具参数，保留原始 JSON 结构
 * @param dependsOn 前置步骤 id 列表；空列表表示无依赖
 */
public record StepSpec(
        String id,
        String tool,
        Map<String, Object> args,
        List<String> dependsOn
) {
}
