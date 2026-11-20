package com.kl.agent.plan;

import com.kl.agent.tool.TestToolCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanValidatorTest {

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

    @Test
    void malformedJsonIsRejectedByParser() {
        assertThatThrownBy(() -> parser.parse(read("malformed/truncated.json")))
                .isInstanceOf(PlanParseException.class);
    }

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
