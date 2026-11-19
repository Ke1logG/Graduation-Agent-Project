package com.kl.agent.plan;

import java.util.List;

/**
 * 计划校验结果。
 */
public record ValidationResult(List<PlanError> errors) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(List.of());
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
