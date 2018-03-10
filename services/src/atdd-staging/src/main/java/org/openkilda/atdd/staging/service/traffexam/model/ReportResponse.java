package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class ReportResponse implements Serializable {
    private IPerfReportRoot report;
    private String error;
    private Integer status;

    @JsonCreator
    public ReportResponse(
            @JsonProperty("report") IPerfReportRoot report,
            @JsonProperty("error") String error,
            @JsonProperty("status") Integer status) {
        this.report = report;
        this.error = error;
        this.status = status;
    }

    public IPerfReportRoot getReport() {
        return report;
    }

    public String getError() {
        return error;
    }

    public Integer getStatus() {
        return status;
    }
}
