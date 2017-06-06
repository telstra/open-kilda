package org.bitbucket.openkilda.messaging.info;

import org.bitbucket.openkilda.messaging.MessageData;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowPathResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowStatusResponse;
import org.bitbucket.openkilda.messaging.info.flow.FlowsResponse;
import org.bitbucket.openkilda.messaging.info.stats.FlowStatsData;
import org.bitbucket.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.bitbucket.openkilda.messaging.info.stats.PortStatsData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the payload of a Message representing an info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "info")
@JsonSubTypes({
        @Type(value = FlowResponse.class, name = "flow"),
        @Type(value = FlowsResponse.class, name = "flows"),
        @Type(value = FlowStatusResponse.class, name = "flow_status"),
        @Type(value = FlowPathResponse.class, name = "flow_path"),
        @Type(value = PathInfoData.class, name = "path"),
        @Type(value = IslInfoData.class, name = "isl"),
        @Type(value = SwitchInfoData.class, name = "switch"),
        @Type(value = PortInfoData.class, name = "port"),
        @Type(value = PortStatsData.class, name = "port_stats"),
        @Type(value = MeterConfigStatsData.class, name = "meter_config_stats"),
        @Type(value = FlowStatsData.class, name = "flow_stats")})
public abstract class InfoData extends MessageData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("Not implemented for: %s", getClass().getSimpleName());
    }
}
