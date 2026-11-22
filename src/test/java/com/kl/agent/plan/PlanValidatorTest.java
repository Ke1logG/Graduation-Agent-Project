package com.kl.agent.plan;

import com.kl.agent.tool.TestToolCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PlanValidator 的夹具测试：合法计划必须零错误，非法计划必须报出指定错误码；
 * 另外覆盖 L3 的步数预算、路径白名单和排序字段白名单。
 */
class PlanValidatorTest {

    /** 8 个合法夹具：每条都应通过 L1–L3 校验。 */
    private static final List<String> VALID_FIXTURES = List.of(
            "valid/two-step.json",
            "valid/single-step.json",
            "valid/parallel-steps.json",
            "valid/file-reader.json",
            "valid/reference-chain.json",
            "valid/empty-depends-on.json",
            "valid/markdown-fenced.json",
            "valid/prose-prefix.json"
    );

    /** 11 个非法夹具：每条都必须报出这里指定的错误码。 */
    private static final Map<String, PlanErrorCode> INVALID_FIXTURES = Map.ofEntries(
            Map.entry("invalid/empty-steps.json", PlanErrorCode.EMPTY_PLAN),
            Map.entry("invalid/unknown-tool.json", PlanErrorCode.UNKNOWN_TOOL),
            Map.entry("invalid/missing-arg.json", PlanErrorCode.MISSING_ARG),
            Map.entry("invalid/arg-type.json", PlanErrorCode.INVALID_ARG_TYPE),
            Map.entry("invalid/duplicate-step-id.json", PlanErrorCode.DUPLICATE_STEP_ID),
            Map.entry("invalid/missing-dependency.json", PlanErrorCode.UNKNOWN_DEPENDENCY),
            Map.entry("invalid/self-dependency.json", PlanErrorCode.SELF_DEPENDENCY),
            Map.entry("invalid/cycle.json", PlanErrorCode.CYCLE),
            Map.entry("invalid/unknown-reference.json", PlanErrorCode.UNKNOWN_REFERENCE),
            Map.entry("invalid/reference-without-dependency.json", PlanErrorCode.MISSING_DEPENDENCY),
            Map.entry("invalid/null-dependencies.json", PlanErrorCode.INVALID_DEPENDENCIES)
    );

    private final PlanParser parser = new PlanParser(new ObjectMapper());
    private final PlanValidator validator = new PlanValidator(new TestToolCatalog());

    /** 8 个合法夹具逐一解析并校验，结果必须零错误。 */
    @Test
    void validFixturesPass() {
        for (String fixture : VALID_FIXTURES) {
            PlanSpec plan = parser.parse(read(fixture));
            ValidationResult result = validator.validate(plan);

            assertThat(result.errors())
                    .as("fixture: %s", fixture)
                    .isEmpty();
        }
    }

    /** 11 个非法夹具：每个都必须报出映射表里指定的错误码。 */
    @Test
    void invalidFixturesAreRejectedWithExpectedCode() {
        for (Map.Entry<String, PlanErrorCode> entry : INVALID_FIXTURES.entrySet()) {
            PlanSpec plan = parser.parse(read(entry.getKey()));
            ValidationResult result = validator.validate(plan);

            assertThat(result.errors())
                    .as("fixture: %s", entry.getKey())
                    .extracting(PlanError::code)
                    .contains(entry.getValue());
        }
    }

    /** JSON 截断的夹具应在解析层（L0）被拒，不进入校验器。 */
    @Test
    void malformedJsonIsRejectedByParser() {
        assertThatThrownBy(() -> parser.parse(read("malformed/truncated.json")))
                .isInstanceOf(PlanParseException.class);
    }

    /** L3 步数预算：21 步超过默认上限 20，应报 TOO_MANY_STEPS。 */
    @Test
    void rejectsTooManySteps() {
        List<StepSpec> steps = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            steps.add(new StepSpec(
                    "s" + i,
                    "calculator",
                    Map.of("expression", "1 + 1"),
                    List.of()));
        }

        assertHasError(new PlanSpec("too many steps", steps), PlanErrorCode.TOO_MANY_STEPS);
    }

    /** L3 路径白名单：含 ../ 的路径穿越应报 UNSAFE_PATH。 */
    @Test
    void rejectsPathTraversal() {
        assertHasError(writerPlan("runs/../secret.json"), PlanErrorCode.UNSAFE_PATH);
    }

    /** L3 路径白名单：相对路径但不在允许前缀（runs/、data/demo/）内应报 UNSAFE_PATH。 */
    @Test
    void rejectsPathOutsideAllowlist() {
        assertHasError(writerPlan("tmp/out.json"), PlanErrorCode.UNSAFE_PATH);
    }

    /** L3 路径白名单：绝对路径（如 C:/...）应报 UNSAFE_PATH。 */
    @Test
    void rejectsAbsolutePath() {
        assertHasError(writerPlan("C:/Windows/out.json"), PlanErrorCode.UNSAFE_PATH);
    }

    /** 对照组：runs/ 下的相对路径应通过校验。 */
    @Test
    void acceptsAllowedWriterPath() {
        assertThat(validator.validate(writerPlan("runs/out.json")).errors()).isEmpty();
    }

    /** L3 排序字段白名单：rank_anime 的 sortBy 不在白名单内应报 INVALID_SORT_FIELD。 */
    @Test
    void rejectsUnknownSortField() {
        assertHasError(rankPlan("title"), PlanErrorCode.INVALID_SORT_FIELD);
    }

    /** 对照组：score 是允许的排序字段，应通过校验。 */
    @Test
    void acceptsAllowedSortField() {
        assertThat(validator.validate(rankPlan("score")).errors()).isEmpty();
    }

    /** 构造单步 writer 计划，用于路径白名单测试。 */
    private PlanSpec writerPlan(String path) {
        return new PlanSpec("write a file", List.of(new StepSpec(
                "s1",
                "writer",
                Map.of("path", path, "content", "hello"),
                List.of())));
    }

    /** 构造单步 rank_anime 计划，用于排序字段白名单测试。 */
    private PlanSpec rankPlan(String sortBy) {
        return new PlanSpec("rank anime", List.of(new StepSpec(
                "s1",
                "rank_anime",
                Map.of("sortBy", sortBy),
                List.of())));
    }

    /** 断言校验结果中包含指定错误码。 */
    private void assertHasError(PlanSpec plan, PlanErrorCode expected) {
        assertThat(validator.validate(plan).errors())
                .extracting(PlanError::code)
                .contains(expected);
    }

    /** 从 classpath 读取夹具文本；资源缺失时直接断言失败。 */
    private String read(String fixture) {
        String path = "/plans/" + fixture;
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("resource: %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }
}
