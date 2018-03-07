package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class IPerfReportRoot implements Serializable {
    public String error;
    public IPerfReportEndBranch end;
}
