package com.kl.agent.engine;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.kl.agent.plan.PlanSpec;
import com.kl.agent.plan.StepSpec;
import com.kl.agent.tool.FakeSearchTool;
import com.kl.agent.tool.FakeWriterTool;
import com.kl.agent.tool.Tool;
import com.kl.agent.tool.ToolRegistry;

class SequentialExecutorTest {

    /**
     * 测试1：正向用例：按计划执行所有步骤
     */
    @Test
    void executesStepsInPlanOrder() {
        FakeWriterTool writer = new FakeWriterTool();
        SequentialExecutor executor = new SequentialExecutor(
                new ToolRegistry(new FakeSearchTool(), writer));

        PlanSpec plan = new PlanSpec("write a result", List.of(
                new StepSpec("s1", "search_anime",
                        Map.of("query", "Sousou no Frieren"), List.of()),
                new StepSpec("s2", "writer",
                        Map.of("path", "runs/out.json", "content", "hello"),
                        List.of("s1"))
        ));

        ExecutionResult result = executor.execute(plan);

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
        assertThat(result.steps())
                .extracting(StepResult::stepId)
                .containsExactly("s1", "s2");
        assertThat(result.outputs()).containsKeys("s1", "s2");
        assertThat(writer.lastArgs()).containsEntry("content", "hello");
    }

    /**
     * 测试2：测试中间某一步出错时立即停止，后续步骤不再执行
     */
    @Test
    void stopsAtFirstFailure() {
        FakeWriterTool writer = new FakeWriterTool();
        Tool failing = failingTool("fail");
        SequentialExecutor executor = new SequentialExecutor(
                new ToolRegistry(new FakeSearchTool(), failing, writer));

        PlanSpec plan = new PlanSpec("stop on failure", List.of(
                new StepSpec("s1", "search_anime",
                        Map.of("query", "Frieren"), List.of()),
                new StepSpec("s2", "fail", Map.of(), List.of("s1")),
                new StepSpec("s3", "writer",
                        Map.of("path", "runs/out.json", "content", "never"),
                        List.of("s2"))
        ));

        ExecutionResult result = executor.execute(plan);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("boom");
        assertThat(result.steps()).hasSize(2);
        assertThat(writer.lastArgs()).isEmpty();
    }

    /**
     * 测试3：计划里用了注册表里不存在的工具，执行失败
     */
    @Test
    void failsWhenToolIsMissing() {
        SequentialExecutor executor = new SequentialExecutor(new ToolRegistry());

        PlanSpec plan = new PlanSpec("missing tool", List.of(
                new StepSpec("s1", "missing", Map.of(), List.of())));

        ExecutionResult result = executor.execute(plan);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("工具不存在");
        assertThat(result.steps()).hasSize(1);
    }

    /**
     * 测试4：s1前置依赖未完成，执行失败
     */
    @Test
    void failsWhenDependencyHasNotRun() {
        FakeWriterTool writer = new FakeWriterTool();
        SequentialExecutor executor = new SequentialExecutor(
                new ToolRegistry(new FakeSearchTool(), writer));

        // 故意把依赖步骤放在后面，顺序执行器不能跳过 s1 去等 s2
        PlanSpec plan = new PlanSpec("out of order", List.of(
                new StepSpec("s1", "writer",
                        Map.of("path", "runs/out.json", "content", "hello"),
                        List.of("s2")),
                new StepSpec("s2", "search_anime",
                        Map.of("query", "Frieren"), List.of())
        ));

        ExecutionResult result = executor.execute(plan);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("依赖步骤未完成");
        assertThat(result.steps()).hasSize(1);
        assertThat(writer.lastArgs()).isEmpty();
    }

    /**
     * 测试5：步骤为空时执行失败
     */
    @Test
    void rejectsEmptyPlan() {
        SequentialExecutor executor = new SequentialExecutor(new ToolRegistry());

        ExecutionResult result = executor.execute(new PlanSpec("empty", List.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("没有步骤");
    }

    /**
     * 辅助方法，构造会出错的工具
     */
    private Tool failingTool(String name) {
        return new Tool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Object execute(Map<String, Object> args) {
                throw new IllegalStateException("boom");
            }
        };
    }
}
