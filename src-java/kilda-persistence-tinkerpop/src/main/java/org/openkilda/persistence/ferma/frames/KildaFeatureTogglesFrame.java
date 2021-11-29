/* Copyright 2021 Telstra Open Source
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

package org.openkilda.persistence.ferma.frames;

import org.openkilda.model.KildaFeatureToggles.KildaFeatureTogglesData;

import com.syncleus.ferma.annotations.Property;

public abstract class KildaFeatureTogglesFrame extends KildaBaseVertexFrame implements KildaFeatureTogglesData {
    public static final String FRAME_LABEL = "kilda_feature_toggles";
    public static final String UNIQUE_PROPERTY = "unique";

    @Override
    @Property("flows_reroute_on_isl_discovery")
    public abstract Boolean getFlowsRerouteOnIslDiscoveryEnabled();

    @Override
    @Property("flows_reroute_on_isl_discovery")
    public abstract void setFlowsRerouteOnIslDiscoveryEnabled(Boolean flowsRerouteOnIslDiscoveryEnabled);

    @Override
    @Property("create_flow")
    public abstract Boolean getCreateFlowEnabled();

    @Override
    @Property("create_flow")
    public abstract void setCreateFlowEnabled(Boolean createFlowEnabled);

    @Override
    @Property("update_flow")
    public abstract Boolean getUpdateFlowEnabled();

    @Override
    @Property("update_flow")
    public abstract void setUpdateFlowEnabled(Boolean updateFlowEnabled);

    @Override
    @Property("delete_flow")
    public abstract Boolean getDeleteFlowEnabled();

    @Override
    @Property("delete_flow")
    public abstract void setDeleteFlowEnabled(Boolean deleteFlowEnabled);

    @Override
    @Property("use_bfd_for_isl_integrity_check")
    public abstract Boolean getUseBfdForIslIntegrityCheck();

    @Override
    @Property("use_bfd_for_isl_integrity_check")
    public abstract void setUseBfdForIslIntegrityCheck(Boolean useBfdForIslIntegrityCheck);

    @Override
    @Property("floodlight_router_periodic_sync")
    public abstract Boolean getFloodlightRoutePeriodicSync();

    @Override
    @Property("floodlight_router_periodic_sync")
    public abstract void setFloodlightRoutePeriodicSync(Boolean floodlightRoutePeriodicSync);

    @Override
    @Property("flows_reroute_using_default_encap_type")
    public abstract Boolean getFlowsRerouteUsingDefaultEncapType();

    @Override
    @Property("flows_reroute_using_default_encap_type")
    public abstract void setFlowsRerouteUsingDefaultEncapType(Boolean flowsRerouteUsingDefaultEncapType);

    @Override
    @Property("collect_grpc_stats")
    public abstract Boolean getCollectGrpcStats();

    @Override
    @Property("collect_grpc_stats")
    public abstract void setCollectGrpcStats(Boolean collectGrpcStats);

    @Override
    @Property("server42_flow_rtt")
    public abstract Boolean getServer42FlowRtt();

    @Override
    @Property("server42_flow_rtt")
    public abstract void setServer42FlowRtt(Boolean server42FlowRtt);

    @Override
    @Property("flow_latency_monitoring_reactions")
    public abstract Boolean getFlowLatencyMonitoringReactions();

    @Override
    @Property("flow_latency_monitoring_reactions")
    public abstract void setFlowLatencyMonitoringReactions(Boolean flowLatencyMonitoringReactions);

    @Property("server42_isl_rtt")
    public abstract Boolean getServer42IslRtt();

    @Override
    @Property("server42_isl_rtt")
    public abstract void setServer42IslRtt(Boolean server42IslRtt);

    @Override
    @Property("modify_y_flow_enabled")
    public abstract Boolean getModifyYFlowEnabled();

    @Override
    @Property("modify_y_flow_enabled")
    public abstract void setModifyYFlowEnabled(Boolean modifyYFlowEnabled);
}
