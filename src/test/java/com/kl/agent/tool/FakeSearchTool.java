package com.kl.agent.tool;

import java.util.List;
import java.util.Map;

/**
 * 测试用搜索工具：返回固定的 items，不访问数据库。
 */
public final class FakeSearchTool implements Tool {

    @Override
    public String name() {
        return "search_anime";
    }

    @Override
    public Object execute(Map<String, Object> args) {
        return Map.of("items", List.of(
                Map.of(
                        "id", 1,
                        "title", "Sousou no Frieren",
                        "score", 9.29
                )
        ));
    }
}
