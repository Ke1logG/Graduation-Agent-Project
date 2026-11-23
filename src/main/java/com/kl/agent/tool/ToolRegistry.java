package com.kl.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 内存工具注册表：按工具名查找 {@link Tool}。
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools;

    public ToolRegistry(Tool... tools) {
        Map<String, Tool> byName = new LinkedHashMap<>();
        for (Tool tool : tools) {
            Objects.requireNonNull(tool, "tool");
            String name = tool.name();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("工具名不能为空");
            }
            if (byName.putIfAbsent(name, tool) != null) {
                throw new IllegalArgumentException("工具名重复: " + name);
            }
        }
        this.tools = Map.copyOf(byName);
    }

    public Optional<Tool> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }
}
