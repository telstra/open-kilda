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

package org.openkilda.pce.provider;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.Flow;

import java.util.List;

/**
 * {@link TopologyRepository} declares operations on Topology entities in a storage.
 */
public interface TopologyRepository {

    /**
     * Gets ISL weight.
     *
     * @param isl an ISL instance.
     * @return the weight of requested ISL.
     */
    long getWeight(IslInfoData isl);

    /**
     * Gets all flows.
     *
     * @return a list of {@link FlowInfo} for all flows.
     */
    List<FlowInfo> getFlowInfo();

    /**
     * Gets all flows.
     *
     * @return a list of {@link Flow} for all flows.
     */
    List<Flow> getAllFlows();

    /**
     * Gets a single flow.
     * <p/>
     * In reality, a single flow will typically be bi-directional, so just represent as a list.
     *
     * @return the flows (forward and reverse) by id, if exist.
     */
    List<Flow> getFlows(String flowId);

    /**
     * Gets all switches.
     *
     * @return a list of {@link SwitchInfoData} for all switches.
     */
    List<SwitchInfoData> getSwitches();

    /**
     * Gets all ISLs.
     *
     * @return a list of {@link IslInfoData} for all ISLs.
     */
    List<IslInfoData> getIsls();

    /**
     * Checks whether the port belongs to any ISL (regardless the source or destination end).
     *
     * @param switchId the switch to check
     * @param port     the port to check
     * @return true if the port is ISL, or false otherwise.
     */
    boolean isIslPort(String switchId, int port);
}
