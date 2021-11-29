/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.model.system;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureTogglesDto implements Serializable {
    @JsonProperty("flows_reroute_on_isl_discovery")
    private Boolean flowsRerouteOnIslDiscoveryEnabled;

    @JsonProperty("create_flow")
    private Boolean createFlowEnabled;

    @JsonProperty("update_flow")
    private Boolean updateFlowEnabled;

    @JsonProperty("delete_flow")
    private Boolean deleteFlowEnabled;

    @JsonProperty("use_bfd_for_isl_integrity_check")
    private Boolean useBfdForIslIntegrityCheck;

    @JsonProperty("floodlight_router_periodic_sync")
    private Boolean floodlightRoutePeriodicSync;

    @JsonProperty("flows_reroute_using_default_encap_type")
    private Boolean flowsRerouteUsingDefaultEncapType;

    @JsonProperty("collect_grpc_stats")
    private Boolean collectGrpcStats;

    @JsonProperty("server42_flow_rtt")
    private Boolean server42FlowRtt;

    @JsonProperty("flow_latency_monitoring_reactions")
    private Boolean flowLatencyMonitoringReactions;

    @JsonProperty("server42_isl_rtt")
    private Boolean server42IslRtt;

    @JsonProperty("modify_y_flow_enabled")
    private Boolean modifyYFlowEnabled;

    @JsonCreator
    public FeatureTogglesDto(@JsonProperty("flows_reroute_on_isl_discovery") Boolean flowsRerouteOnIslDiscoveryEnabled,
                             @JsonProperty("create_flow") Boolean createFlowEnabled,
                             @JsonProperty("update_flow") Boolean updateFlowEnabled,
                             @JsonProperty("delete_flow") Boolean deleteFlowEnabled,
                             @JsonProperty("use_bfd_for_isl_integrity_check") Boolean useBfdForIslIntegrityCheck,
                             @JsonProperty("floodlight_router_periodic_sync") Boolean floodlightRoutePeriodicSync,
                             @JsonProperty("flows_reroute_using_default_encap_type")
                                         Boolean flowsRerouteUsingDefaultEncapType,
                             @JsonProperty("collect_grpc_stats") Boolean collectGrpcStats,
                             @JsonProperty("server42_flow_rtt") Boolean server42FlowRtt,
                             @JsonProperty("flow_latency_monitoring_reactions")
                                         Boolean flowLatencyMonitoringReactions,
                             @JsonProperty("server42_isl_rtt") Boolean server42IslRtt,
                             @JsonProperty("modify_y_flow_enabled") Boolean modifyYFlowEnabled) {
        this.flowsRerouteOnIslDiscoveryEnabled = flowsRerouteOnIslDiscoveryEnabled;
        this.createFlowEnabled = createFlowEnabled;
        this.updateFlowEnabled = updateFlowEnabled;
        this.deleteFlowEnabled = deleteFlowEnabled;
        this.useBfdForIslIntegrityCheck = useBfdForIslIntegrityCheck;
        this.floodlightRoutePeriodicSync = floodlightRoutePeriodicSync;
        this.flowsRerouteUsingDefaultEncapType = flowsRerouteUsingDefaultEncapType;
        this.collectGrpcStats = collectGrpcStats;
        this.server42FlowRtt = server42FlowRtt;
        this.flowLatencyMonitoringReactions = flowLatencyMonitoringReactions;
        this.server42IslRtt = server42IslRtt;
        this.modifyYFlowEnabled = modifyYFlowEnabled;
    }
}
