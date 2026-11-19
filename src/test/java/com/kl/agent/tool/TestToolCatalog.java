package com.kl.agent.tool;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 测试用最小工具目录；P1 的真实 ToolRegistry 会替代它。
 */
public final class TestToolCatalog implements ToolCatalog {

    private static final Map<String, ToolSchema> TOOLS = Map.of(
            "search_anime", schema("search_anime",
                    Map.of("query", String.class, "limit", Number.class),
                    "query"),
            "get_anime_detail", schema("get_anime_detail",
                    Map.of("animeId", Number.class),
                    "animeId"),
            "filter_anime", schema("filter_anime",
                    Map.of("genre", String.class, "type", String.class,
                            "minYear", Number.class, "minScore", Number.class,
                            "maxEpisodes", Number.class)),
            "rank_anime", schema("rank_anime",
                    Map.of("animeIds", java.util.List.class, "sortBy", String.class,
                            "order", String.class, "limit", Number.class)),
            "compare_anime", schema("compare_anime",
                    Map.of("animeIds", java.util.List.class),
                    "animeIds"),
            "calculator", schema("calculator",
                    Map.of("expression", String.class),
                    "expression"),
            "writer", schema("writer",
                    Map.of("path", String.class, "content", String.class),
                    "path", "content"),
            "file_reader", schema("file_reader",
                    Map.of("path", String.class),
                    "path")
    );

    @Override
    public Optional<ToolSchema> find(String toolName) {
        if (toolName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TOOLS.get(toolName));
    }

    private static ToolSchema schema(String name,
                                     Map<String, Class<?>> argumentTypes,
                                     String... requiredArguments) {
        return new ToolSchema(name, argumentTypes, Set.of(requiredArguments));
    }
}
