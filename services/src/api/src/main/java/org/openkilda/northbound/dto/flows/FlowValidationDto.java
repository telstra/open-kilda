package org.openkilda.northbound.dto.flows;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

/**
 * The response for flow validation request.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowValidationDto {

    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("as_expected")
    private Boolean asExpected;

    @JsonProperty("pkt_counts")
    private List<Long> pktCounts;

    @JsonProperty("byte_counts")
    private List<Long> byteCounts;

    private List<PathDiscrepancyDto> discrepancies;

    @JsonProperty("flow_rules_total")
    private Integer flowRulesTotal;

    @JsonProperty("switch_rules_total")
    private Integer switchRulesTotal;

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public Boolean getAsExpected() {
        return asExpected;
    }

    public void setAsExpected(Boolean asExpected) {
        this.asExpected = asExpected;
    }

    public List<Long> getPktCounts() {
        return pktCounts;
    }

    public void setPktCounts(List<Long> pktCounts) {
        this.pktCounts = pktCounts;
    }

    public List<Long> getByteCounts() {
        return byteCounts;
    }

    public void setByteCounts(List<Long> byteCounts) {
        this.byteCounts = byteCounts;
    }

    public List<PathDiscrepancyDto> getDiscrepancies() {
        return discrepancies;
    }

    public void setDiscrepancies(List<PathDiscrepancyDto> discrepancies) {
        this.discrepancies = discrepancies;
    }

    public Integer getFlowRulesTotal() {
        return flowRulesTotal;
    }

    public void setFlowRulesTotal(Integer flowRulesTotal) {
        this.flowRulesTotal = flowRulesTotal;
    }

    public Integer getSwitchRulesTotal() {
        return switchRulesTotal;
    }

    public void setSwitchRulesTotal(Integer switchRulesTotal) {
        this.switchRulesTotal = switchRulesTotal;
    }
}
