package org.openkilda.messaging.ctrl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.storm.task.TopologyContext;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DumpStateResponseData extends ResponseData {
    @JsonProperty("state")
    private AbstractDumpState state;

    @JsonCreator
    public DumpStateResponseData(
            @JsonProperty("component") String component,
            @JsonProperty("task_id") Integer taskId,
            @JsonProperty("topology") String topology,
            @JsonProperty("state") AbstractDumpState state) {
        super(component, taskId, topology);
        this.state = state;
    }

    public DumpStateResponseData(TopologyContext context, String topology, AbstractDumpState state) {
        super(context, topology);
        this.state = state;
    }
}
