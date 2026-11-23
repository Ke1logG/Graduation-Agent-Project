package com.kl.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    @Test
    void findsRegisteredTool() {
        FakeSearchTool search = new FakeSearchTool();
        ToolRegistry registry = new ToolRegistry(search, new FakeWriterTool());

        assertThat(registry.find("search_anime")).containsSame(search);
        assertThat(registry.find("writer")).isPresent();
    }

    @Test
    void returnsEmptyForUnknownOrNullName() {
        ToolRegistry registry = new ToolRegistry(new FakeSearchTool());

        assertThat(registry.find("teleport")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    void rejectsDuplicateToolNames() {
        Tool first = namedTool("same");
        Tool second = namedTool("same");

        assertThatThrownBy(() -> new ToolRegistry(first, second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工具名重复");
    }

    @Test
    void rejectsNullTool() {
        assertThatThrownBy(() -> new ToolRegistry((Tool) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registeredToolCanExecute() {
        ToolRegistry registry = new ToolRegistry(new FakeSearchTool());

        Object output = registry.find("search_anime")
                .orElseThrow()
                .execute(Map.of("query", "Frieren"));

        assertThat(output).isInstanceOf(Map.class);
    }

    private Tool namedTool(String name) {
        return new Tool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Object execute(Map<String, Object> args) {
                return Map.of();
            }
        };
    }
}
