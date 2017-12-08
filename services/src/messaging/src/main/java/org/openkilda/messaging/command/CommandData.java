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

package org.openkilda.messaging.command;

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.command.discovery.DiscoverPathCommandData;
import org.openkilda.messaging.command.discovery.DiscoveryFilterPopulateData;
import org.openkilda.messaging.command.discovery.HealthCheckCommandData;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.command.flow.DefaultFlowsCommandData;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowGetRequest;
import org.openkilda.messaging.command.flow.FlowPathRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowRestoreRequest;
import org.openkilda.messaging.command.flow.FlowStatusRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsGetRequest;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the payload of a Message representing an command.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "command")
@JsonSubTypes({
        @Type(value = FlowStatusRequest.class, name = "flow_status"),
        @Type(value = FlowCreateRequest.class, name = "flow_create"),
        @Type(value = FlowUpdateRequest.class, name = "flow_update"),
        @Type(value = FlowDeleteRequest.class, name = "flow_delete"),
        @Type(value = FlowRestoreRequest.class, name = "flow_restore"),
        @Type(value = FlowsGetRequest.class, name = "flows_get"),
        @Type(value = FlowGetRequest.class, name = "flow_get"),
        @Type(value = FlowPathRequest.class, name = "flow_path"),
        @Type(value = DefaultFlowsCommandData.class, name = "install_default_flows"),
        @Type(value = InstallIngressFlow.class, name = "install_ingress_flow"),
        @Type(value = InstallEgressFlow.class, name = "install_egress_flow"),
        @Type(value = InstallTransitFlow.class, name = "install_transit_flow"),
        @Type(value = InstallOneSwitchFlow.class, name = "install_one_switch_flow"),
        @Type(value = RemoveFlow.class, name = "delete_flow"),
        @Type(value = DiscoverIslCommandData.class, name = "discover_isl"),
        @Type(value = DiscoverPathCommandData.class, name = "discover_path"),
        @Type(value = FlowRerouteRequest.class, name = "flow_reroute"),
        @Type(value = NetworkCommandData.class, name = "network"),
        @Type(value = HealthCheckCommandData.class, name = "health_check"),
        @Type(value = DiscoveryFilterPopulateData.class, name = "populate_isl_filter")})
public abstract class CommandData extends MessageData {
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
