package com.kl.agent.tool;

import java.util.Map;

/**
 * 最小工具契约。
 *
 * <p>工具只负责执行一次具体动作；计划校验、调度、重试和记录由引擎负责。</p>
 */
public interface Tool {

    String name();

    /**
     * 执行工具并返回输出。输出可以是 Map、List 或简单值，供后续步骤引用。
     */
    Object execute(Map<String, Object> args);
}
