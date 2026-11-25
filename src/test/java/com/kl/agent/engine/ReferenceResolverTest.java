package com.kl.agent.engine;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class ReferenceResolverTest {

    /**
     * 引入解析器并准备outputs
     */
    private final ReferenceResolver resolver = new ReferenceResolver();

    private final Map<String, Object> outputs = Map.of(
            "s1", Map.of("items", List.of(
                    Map.of("title", "Sousou no Frieren", "score", 9.29)
            )),
            "s2", Map.of("count", 3)
    );

    /**
     * 测试1：整个列表引用解析，预期解析出准确的list对象
     */
    @Test
    void resolvesWholeStringReferenceToList() {
        Map<String, Object> resolved = resolver.resolveArgs(
                Map.of("content", "$s1.items"), outputs);

        assertThat(resolved.get("content")).isInstanceOf(List.class);
    }

    /**
     * 测试2：解析带列表下标的深层字段引用，预期返回字段的准确数据
     */
    @Test
    void resolvesIndexedNestedField() {
        Map<String, Object> resolved = resolver.resolveArgs(
                Map.of("score", "$s1.items[0].score"), outputs);

        assertThat(resolved.get("score")).isEqualTo(9.29);
    }

    /**
     * 测试3：验证参数本身嵌套场景下能准确解析
     */
    @Test
    void resolvesNestedMapsAndLists() {
        Map<String, Object> resolved = resolver.resolveArgs(Map.of(
                "payload", Map.of("title", "$s1.items[0].title"),
                "values", List.of("$s2.count", "literal")
        ), outputs);

        assertThat(resolved.get("payload"))
                .isEqualTo(Map.of("title", "Sousou no Frieren"));
        assertThat(resolved.get("values")).isEqualTo(List.of(3, "literal"));
    }

    /**
     * 测试4：验证参数为普通字符串时原样保留
     */
    @Test
    void leavesPlainStringsUnchanged() {
        Map<String, Object> resolved = resolver.resolveArgs(
                Map.of("path", "runs/out.json"), outputs);

        assertThat(resolved.get("path")).isEqualTo("runs/out.json");
    }

    /**
     * 测试5：引用的步骤不存在，预期报错
     */
    @Test
    void throwsWhenStepIsMissing() {
        assertThatThrownBy(() -> resolver.resolveArgs(
                Map.of("content", "$s9.items"), outputs))
                .isInstanceOf(ReferenceResolutionException.class)
                .hasMessageContaining("未执行");
    }

    /**
     * 测试6：引用的字段不存在，预期报错
     */
    @Test
    void throwsWhenFieldIsMissing() {
        assertThatThrownBy(() -> resolver.resolveArgs(
                Map.of("content", "$s1.missing"), outputs))
                .isInstanceOf(ReferenceResolutionException.class)
                .hasMessageContaining("字段不存在");
    }

    /**
     * 测试7：引用下标越界，预期报错
     */
    @Test
    void throwsWhenListIndexIsOutOfBounds() {
        assertThatThrownBy(() -> resolver.resolveArgs(
                Map.of("content", "$s1.items[5].title"), outputs))
                .isInstanceOf(ReferenceResolutionException.class)
                .hasMessageContaining("下标越界");
    }
}
