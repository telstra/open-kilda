package org.bitbucket.openkilda.floodlight.message.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Objects;

/**
 * Class represents transit flow installation info.
 *
 * There is no output action for this type of flow.
 *
 * Created by jonv on 23/3/17.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "destination",
        "cookie",
        "switch_id",
        "input_port",
        "output_port",
        "transit_vlan_id",
})
public class InstallTransitFlow extends AbstractInstallFlow {

    /** The transit vlan id value. It is a mandatory parameter. */
    protected Number transitVlanId;

    /** Default constructor. */
    public InstallTransitFlow() {}

    /**
     * Constructs a transit flow installation command.
     *
     * @param cookie         Flow cookie
     * @param switchId       Switch ID for flow installation
     * @param inputPort      Input port of the flow
     * @param outputPort     Output port of the flow
     * @param transitVlanId  Transit vlan id value
     * @throws IllegalArgumentException if any of parameters parameters is null
     */
    @JsonCreator
    public InstallTransitFlow(@JsonProperty("cookie") String cookie,
                              @JsonProperty("switch_id") String switchId,
                              @JsonProperty("input_port") Number inputPort,
                              @JsonProperty("output_port") Number outputPort,
                              @JsonProperty("transit_vlan_id") Number transitVlanId) {
        super(cookie, switchId, inputPort, outputPort);

        setTransitVlanId(transitVlanId);
    }

    /**
     * Returns transit vlan id of the flow.
     *
     * @return Transit vlan id of the flow
     */
    @JsonProperty("transit_vlan_id")
    public Number getTransitVlanId() {
        return transitVlanId;
    }

    /**
     * Sets transit vlan id of the flow.
     *
     * @param transitVlanId vlan id of the flow
     */
    @JsonProperty("transit_vlan_id")
    public void setTransitVlanId(Number transitVlanId) {
        if (transitVlanId == null) {
            throw new IllegalArgumentException("need to set transit_vlan_id");
        } if (!Utils.checkVlanRange(transitVlanId.intValue()) || transitVlanId.intValue() == 0) {
            throw new IllegalArgumentException("need to set valid value for transit_vlan_id");
        }
        this.transitVlanId = transitVlanId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .addValue(cookie)
                .addValue(switchId)
                .addValue(inputPort)
                .addValue(outputPort)
                .addValue(transitVlanId)
                .toString();
    }
}
