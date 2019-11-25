/* Copyright 2019 Telstra Open Source
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

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertPathsSwapAction<T extends FlowPathSwappingFsm<T, S, E, C>, S, E, C>
        extends FlowProcessingAction<T, S, E, C> {
    public RevertPathsSwapAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId(), FetchStrategy.DIRECT_RELATIONS);

            if (stateMachine.getNewEncapsulationType() != null) {
                flow.setEncapsulationType(stateMachine.getOriginalEncapsulationType());
            }

            if (stateMachine.hasOldPrimaryForwardPath()) {
                FlowPath oldForward = getFlowPath(stateMachine.getOldPrimaryForwardPath());
                if (oldForward.getStatus() != FlowPathStatus.ACTIVE) {
                    flowPathRepository.updateStatus(oldForward.getPathId(),
                            stateMachine.getOldPrimaryForwardPathStatus());
                }

                log.debug("Swapping back the primary forward path {} with {}",
                        flow.getForwardPathId(), oldForward.getPathId());

                flow.setForwardPath(oldForward.getPathId());

                saveHistory(stateMachine, flow.getFlowId(), oldForward.getPathId());
            }

            if (stateMachine.hasOldPrimaryReversePath()) {
                FlowPath oldReverse = getFlowPath(stateMachine.getOldPrimaryReversePath());
                if (oldReverse.getStatus() != FlowPathStatus.ACTIVE) {
                    flowPathRepository.updateStatus(oldReverse.getPathId(),
                            stateMachine.getOldPrimaryReversePathStatus());
                }

                log.debug("Swapping back the primary reverse path {} with {}",
                        flow.getReversePathId(), oldReverse.getPathId());

                flow.setReversePath(oldReverse.getPathId());

                saveHistory(stateMachine, flow.getFlowId(), oldReverse.getPathId());
            }

            if (stateMachine.hasOldProtectedForwardPath()) {
                FlowPath oldForward = getFlowPath(stateMachine.getOldProtectedForwardPath());
                if (oldForward.getStatus() != FlowPathStatus.ACTIVE) {
                    flowPathRepository.updateStatus(oldForward.getPathId(),
                            stateMachine.getOldProtectedForwardPathStatus());
                }

                log.debug("Swapping back the protected forward path {} with {}",
                        flow.getProtectedForwardPathId(), oldForward.getPathId());

                flow.setProtectedForwardPath(oldForward.getPathId());

                saveHistory(stateMachine, flow.getFlowId(), oldForward.getPathId());
            }

            if (stateMachine.hasOldProtectedReversePath()) {
                FlowPath oldReverse = getFlowPath(stateMachine.getOldProtectedReversePath());
                if (oldReverse.getStatus() != FlowPathStatus.ACTIVE) {
                    flowPathRepository.updateStatus(oldReverse.getPathId(),
                            stateMachine.getOldProtectedReversePathStatus());
                }

                log.debug("Swapping back the protected reverse path {} with {}",
                        flow.getProtectedReversePathId(), oldReverse.getPathId());

                flow.setProtectedReversePath(oldReverse.getPathId());

                saveHistory(stateMachine, flow.getFlowId(), oldReverse.getPathId());
            }

            flowRepository.createOrUpdate(flow);
        });
    }

    private void saveHistory(T stateMachine, String flowId, PathId pathId) {
        stateMachine.saveActionToHistory("Flow was reverted to old paths",
                format("The flow %s was updated with the path %s", flowId, pathId));
    }
}
