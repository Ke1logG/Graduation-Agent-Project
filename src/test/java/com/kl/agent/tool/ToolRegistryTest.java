package com.kl.agent.tool;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    //注册两个工具进入工具表，断言find方法能找到这两个工具
    @Test
    void findsRegisteredTool() {
        FakeSearchTool search = new FakeSearchTool();
        ToolRegistry registry = new ToolRegistry(search, new FakeWriterTool());

        assertThat(registry.find("search_anime")).containsSame(search);
        assertThat(registry.find("writer")).isPresent();
    }

    //断言找不存在的工具返回空
    @Test
    void returnsEmptyForUnknownOrNullName() {
        ToolRegistry registry = new ToolRegistry(new FakeSearchTool());

        assertThat(registry.find("teleport")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    //创造两个同名工具，断言报错工具名错误
    @Test
    void rejectsDuplicateToolNames() {
        Tool first = namedTool("same");
        Tool second = namedTool("same");

        assertThatThrownBy(() -> new ToolRegistry(first, second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工具名重复");
    }

    //断言传空工具报空指针异常
    @Test
    void rejectsNullTool() {
        assertThatThrownBy(() -> new ToolRegistry((Tool) null))
                .isInstanceOf(NullPointerException.class);
    }

    //完整链路，验证从加入工具表到取出工具到执行工具方法（断言返回map）的全链路正确
    @Test
    void registeredToolCanExecute() {
        ToolRegistry registry = new ToolRegistry(new FakeSearchTool());

        Object output = registry.find("search_anime")
                .orElseThrow()
                .execute(Map.of("query", "Frieren"));

        assertThat(output).isInstanceOf(Map.class);
    }

    //辅助方法：匿名内部类创建一个可以随意命名的工具，便于同名工具类情况测试
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
