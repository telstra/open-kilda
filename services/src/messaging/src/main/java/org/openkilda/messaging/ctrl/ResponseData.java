package org.openkilda.messaging.ctrl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.storm.task.TopologyContext;

import java.io.Serializable;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "action")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DumpStateResponseData.class, name = "dump")})
public class ResponseData implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("component")
    private String component;

    @JsonProperty("task_id")
    private Integer taskId;

    @JsonProperty("topology")
    private String topology;

    /**
     * Specify the component directly.
     *
     * @param component The component
     * @param taskId    The taskId
     * @param topology  The topology
     */
    public ResponseData(
            @JsonProperty("component") String component,
            @JsonProperty("task_id") Integer taskId,
            @JsonProperty("topology") String topology) {
        this.component = component;
        this.taskId = taskId;
        this.topology = topology;
    }

    /**
     * Derive the component from the context.
     *
     * @param context  The context
     * @param topology  The topology
     */
    public ResponseData(TopologyContext context, String topology) {
        this.component = context.getThisComponentId();
        this.taskId = context.getThisTaskId();
        this.topology = topology;
    }

    public String getComponent() {
        return component;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public String getTopology() {
        return topology;
    }
}
