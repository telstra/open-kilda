package org.bitbucket.openkilda.messaging.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.MoreObjects;

/**
 * Class represents flow deletion info.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "flow_name",
        "switch_id",
        "meter_id"
})
public class DeleteFlow extends AbstractFlow {

    protected Long meterId;

    /**
     * Constructs a flow deletion command.
     *
     * @param flowName     Flow name
     * @param switchId     Switch ID for flow installation
     * @throws IllegalArgumentException if any of parameters parameters is null
     */
    @JsonCreator
    public DeleteFlow(@JsonProperty("flow_name") String flowName,
                      @JsonProperty("switch_id") String switchId,
                      @JsonProperty("meter_id") Long meterId) {
        super(flowName, switchId);
        setMeterId(meterId);
    }

    @JsonProperty("meter_id")
    public Long getMeterId() {
        return meterId;
    }

    @JsonProperty("meter_id")
    public void setMeterId(Long meterId) {
        this.meterId = meterId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(flowName)
                .addValue(switchId)
                .addValue(meterId)
                .toString();
    }
}
