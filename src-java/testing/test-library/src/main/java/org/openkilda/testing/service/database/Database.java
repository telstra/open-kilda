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

package org.openkilda.testing.service.database;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMeter;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.YFlow;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface Database {

    // ISLs

    boolean updateIslMaxBandwidth(Isl isl, long value);

    boolean updateIslsMaxBandwidth(List<Isl> isl, long value);

    boolean updateIslAvailableBandwidth(Isl isl, long value);

    boolean updateIslsAvailableBandwidth(List<Isl> isls, long value);

    boolean updateIslsAvailableAndMaxBandwidth(List<Isl> islsToUpdate, long availableValue, long maxValue);

    boolean resetIslBandwidth(Isl isl);

    boolean resetIslsBandwidth(List<Isl> isls);

    int getIslCost(Isl isl);

    boolean updateIslCost(Isl isl, int value);

    boolean updateIslLatency(Isl isl, long latency);

    boolean updateIslsLatency(List<Isl> isls, long latency);

    boolean resetCosts();

    boolean resetCosts(List<Isl> isls);

    boolean updateIslTimeUnstable(Isl isl, Instant newTimeUnstable);

    Instant getIslTimeUnstable(Isl isl);

    List<org.openkilda.model.Isl> getIsls(List<Isl> isls);

    // Switches

    Switch getSwitch(SwitchId switchId);

    void setSwitchStatus(SwitchId switchId, SwitchStatus swStatus);

    void setSwitchFeatures(SwitchId switchId, Set<SwitchFeature> switchFeatures);

    List<PathInfoData> getPaths(SwitchId src, SwitchId dst);

    void removeConnectedDevices(SwitchId sw);

    boolean removeInactiveSwitches();

    // Flows

    int countFlows();

    Flow getFlow(String flowId);

    Collection<TransitVlan> getTransitVlans(PathId forwardPathId, PathId reversePathId);

    Optional<TransitVlan> getTransitVlan(PathId pathId);

    void updateFlowBandwidth(String flowId, long newBw);

    void updateFlowMeterId(String flowId, long newMeterId);

    List<FlowMirrorPoints> getMirrorPoints();

    //HA-Flows

    Set<Cookie> getHaFlowCookies(String haFlowId);

    Set<Cookie> getHaSubFlowsCookies(List<String> haSubFlowIds);

    Set<FlowMeter> getHaFlowMeters(List<String> haSubFlowIds, String haFlowId);

    //Y-Flows

    YFlow getYFlow(String yFlowId);

    //history
    void addFlowEvent(FlowEvent event);

    //misc

    List<Object> dumpAllNodes();

    List<Object> dumpAllRelations();

    List<Object> dumpAllSwitches();

    List<Object> dumpAllIsls();
}
