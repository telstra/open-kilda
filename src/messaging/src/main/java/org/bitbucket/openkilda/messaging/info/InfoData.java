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
import org.bitbucket.openkilda.messaging.info.flow.FlowsStatusResponse;

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
        property = "message_type")
@JsonSubTypes({
        @Type(value = FlowResponse.class, name = "flow"),
        @Type(value = FlowsResponse.class, name = "flows"),
        @Type(value = FlowStatusResponse.class, name = "flow_status"),
        @Type(value = FlowsStatusResponse.class, name = "flows_status"),
        @Type(value = FlowPathResponse.class, name = "flow_path"),
        @Type(value = PathInfoData.class, name = "path"),
        @Type(value = IslInfoData.class, name = "isl"),
        @Type(value = SwitchInfoData.class, name = "switch"),
        @Type(value = PortInfoData.class, name = "port")})
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
        return "Not implemented for " + getClass().getCanonicalName();
    }
}
