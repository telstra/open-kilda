package org.openkilda.northbound.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.model.Flow;

import java.util.List;

/**
 * The response for flow validation request.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowValidationDto {

    @JsonProperty("flow_id")
    private String flowId;

    private Flow flow;

    @JsonProperty("as_expected")
    private Boolean asExpected;

    private List<PathDiscrepancyDto> discrepancies;

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public Flow getFlow() {
        return flow;
    }

    public void setFlow(Flow flow) {
        this.flow = flow;
    }

    public Boolean getAsExpected() {
        return asExpected;
    }

    public void setAsExpected(Boolean asExpected) {
        this.asExpected = asExpected;
    }

    public List<PathDiscrepancyDto> getDiscrepancies() {
        return discrepancies;
    }

    public void setDiscrepancies(List<PathDiscrepancyDto> discrepancies) {
        this.discrepancies = discrepancies;
    }
}
