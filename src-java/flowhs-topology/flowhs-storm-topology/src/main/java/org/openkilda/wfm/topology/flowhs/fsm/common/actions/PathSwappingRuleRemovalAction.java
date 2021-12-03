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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

public abstract class PathSwappingRuleRemovalAction<T extends FlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E, C> extends
        BaseFlowRuleRemovalAction<T, S, E, C> {

    public PathSwappingRuleRemovalAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager, resourcesManager);
    }

    protected Flow getOriginalFlowWithPaths(FlowPathSwappingFsm<T, S, E, C, ?, ?> stateMachine,
                                            RequestedFlow originalFlow) {
        Flow flow = RequestedFlowMapper.INSTANCE.toFlow(originalFlow);
        flow.setForwardPathId(stateMachine.getOldPrimaryForwardPath());
        flow.setReversePathId(stateMachine.getOldPrimaryReversePath());
        if (flow.isAllocateProtectedPath()) {
            flow.setProtectedForwardPathId(stateMachine.getOldProtectedForwardPath());
            flow.setProtectedReversePathId(stateMachine.getOldProtectedReversePath());
        }
        flow.addPaths(getFlow(stateMachine.getFlowId()).getPaths().toArray(new FlowPath[0]));
        return flow;
    }
}
