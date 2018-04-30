package org.openkilda.northbound.dto.flows;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.ToString;

/**
 * PathDiscrepancyDto is used to capture differences between expected and actual values in a path.
 * One example of its use is in comparing a Flow, as expected based on the database, and as
 * found (actual) on switches.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class PathDiscrepancyDto {

    /** The rule will be a hybrid of what is expected */
    private String rule;
    private String field;

    @JsonProperty("expected_value")
    private String expectedValue;

    @JsonProperty("actual_value")
    private String actualValue;

    @JsonCreator
    public PathDiscrepancyDto(
            @JsonProperty("rule") String rule,
            @JsonProperty("field") String field,
            @JsonProperty("expected_value") String expectedValue,
            @JsonProperty("actual_value") String actualValue) {
        this.rule = rule;
        this.field = field;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    public String getActualValue() {
        return actualValue;
    }

    public void setActualValue(String actualValue) {
        this.actualValue = actualValue;
    }
}
