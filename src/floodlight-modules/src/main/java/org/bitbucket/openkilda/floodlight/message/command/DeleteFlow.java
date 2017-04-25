package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Objects;

/**
 * Class represents flow deletion info.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "cookie",
        "switch_id",
        "meter_id"
})
public class DeleteFlow extends AbstractFlow {

    protected Long meterId;

    /** Default constructor. */
    public DeleteFlow() {}

    /**
     * Constructs a flow deletion command.
     *
     * @param cookie       Flow cookie
     * @param switchId     Switch ID for flow installation
     * @throws IllegalArgumentException if any of parameters parameters is null
     */
    @JsonCreator
    public DeleteFlow(@JsonProperty("cookie") String cookie,
                      @JsonProperty("switch_id") String switchId,
                      @JsonProperty("meter_id") Long meterId) {
        super(cookie, switchId);
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
        return Objects.toStringHelper(this)
                .addValue(cookie)
                .addValue(switchId)
                .addValue(meterId)
                .toString();
    }
}
