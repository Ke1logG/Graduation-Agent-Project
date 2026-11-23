package com.kl.agent.tool;

import java.util.Map;

/**
 * 测试用 writer：把收到的参数保存在内存里，方便断言，不写真实文件。
 */
public final class FakeWriterTool implements Tool {

    private Map<String, Object> lastArgs = Map.of();

    @Override
    public String name() {
        return "writer";
    }

    @Override
    public Object execute(Map<String, Object> args) {
        this.lastArgs = args == null ? Map.of() : Map.copyOf(args);
        return Map.of("written", true);
    }

    public Map<String, Object> lastArgs() {
        return lastArgs;
    }
}
