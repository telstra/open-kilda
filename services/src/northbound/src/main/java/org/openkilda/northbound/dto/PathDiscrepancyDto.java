package org.openkilda.northbound.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.info.event.PathNode;

/**
 * PathDiscrepancyDto is used to capture differences between expected and actual values in a path.
 * One example of its use is in comparing a Flow, as expected based on the database, and as
 * found (actual) on switches.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PathDiscrepancyDto {

    private PathNode node;
    private String field;

    @JsonProperty("expected_value")
    private String expectedValue;

    @JsonProperty("actual_value")
    private String actualValue;


    public PathNode getNode() {
        return node;
    }

    public void setNode(PathNode node) {
        this.node = node;
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
