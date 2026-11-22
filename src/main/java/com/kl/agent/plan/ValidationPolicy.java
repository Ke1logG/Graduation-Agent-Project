package com.kl.agent.plan;

import java.util.Objects;
import java.util.Set;

/**
 * L3 执行前策略：限制计划规模、输出路径和排序字段。
 *
 * @param maxSteps            单个计划允许的最大步骤数
 * @param allowedPathPrefixes 允许的文件路径前缀（统一使用 /）
 * @param allowedSortFields   rank_anime 允许的排序字段
 */
public record ValidationPolicy(
        int maxSteps,
        Set<String> allowedPathPrefixes,
        Set<String> allowedSortFields
) {

    public ValidationPolicy {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps 必须大于 0");
        }
        Objects.requireNonNull(allowedPathPrefixes, "allowedPathPrefixes");
        Objects.requireNonNull(allowedSortFields, "allowedSortFields");
        allowedPathPrefixes = Set.copyOf(allowedPathPrefixes);
        allowedSortFields = Set.copyOf(allowedSortFields);
    }

    public static ValidationPolicy defaults() {
        return new ValidationPolicy(
                20,
                Set.of("runs/", "data/demo/"),
                Set.of("score", "members", "startYear", "episodes", "rank", "popularity")
        );
    }
}
