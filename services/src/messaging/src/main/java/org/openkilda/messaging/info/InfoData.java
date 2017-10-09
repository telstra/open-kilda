/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.messaging.info;

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.info.discovery.HealthCheckInfoData;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.info.flow.FlowPathResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.info.stats.FlowStatsData;
import org.openkilda.messaging.info.stats.MeterConfigStatsData;
import org.openkilda.messaging.info.stats.PortStatsData;

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
        @Type(value = FlowPathResponse.class, name = "flow_path"),
        @Type(value = FlowInfoData.class, name = "flow_operation"),
        @Type(value = PathInfoData.class, name = "path"),
        @Type(value = IslInfoData.class, name = "isl"),
        @Type(value = SwitchInfoData.class, name = "switch"),
        @Type(value = PortInfoData.class, name = "port"),
        @Type(value = PortStatsData.class, name = "port_stats"),
        @Type(value = MeterConfigStatsData.class, name = "meter_config_stats"),
        @Type(value = FlowStatsData.class, name = "flow_stats"),
        @Type(value = NetworkInfoData.class, name = "network"),
        @Type(value = HealthCheckInfoData.class, name = "health_check")})
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
