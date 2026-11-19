package com.kl.agent.tool;

import java.util.Optional;

/**
 * 工具目录的最小接口；P1 的工具注册表会实现它。
 */
@FunctionalInterface
public interface ToolCatalog {

    Optional<ToolSchema> find(String toolName);
}
