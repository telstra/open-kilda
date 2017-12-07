package org.openkilda.domain.floodlight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowItem {

    private String version;
    @JsonProperty(value = "table_id")
    private String tableId;
    private String cookie;
    @JsonProperty(value = "byte_count")
    private String byteCount;
    private String priority;
    @JsonProperty(value = "instructions")
    private FlowInstructions flowInstructions;

    public FlowItem() {
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public String getByteCount() {
        return byteCount;
    }

    public void setByteCount(String byteCount) {
        this.byteCount = byteCount;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public FlowInstructions getFlowInstructions() {
        return flowInstructions;
    }

    public void setFlowInstructions(FlowInstructions flowInstructions) {
        this.flowInstructions = flowInstructions;
    }
}
