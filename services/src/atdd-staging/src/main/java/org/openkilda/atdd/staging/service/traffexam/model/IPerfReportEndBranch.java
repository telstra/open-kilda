package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class IPerfReportEndBranch implements Serializable {
    public IPerfReportSumBranch sum;

    @JsonProperty("sum_sent")
    public IPerfReportTcpSumSection sumSent;

    @JsonProperty("sum_received")
    public IPerfReportTcpSumSection sumReceived;
}
