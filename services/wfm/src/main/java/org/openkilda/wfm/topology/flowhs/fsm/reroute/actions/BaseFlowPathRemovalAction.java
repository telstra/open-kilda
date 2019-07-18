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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * A base for action classes that remove flow paths.
 */
@Slf4j
abstract class BaseFlowPathRemovalAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    protected final TransactionManager transactionManager;
    protected final FlowPathRepository flowPathRepository;
    private final IslRepository islRepository;

    BaseFlowPathRemovalAction(PersistenceManager persistenceManager) {
        super(persistenceManager);

        transactionManager = persistenceManager.getTransactionManager();
        flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
    }

    protected void deleteFlowPaths(FlowPath forwardPath, FlowPath reversePath) {
        flowPathRepository.delete(forwardPath);
        flowPathRepository.delete(reversePath);

        updateIslsForFlowPath(forwardPath, reversePath);
    }

    private void updateIslsForFlowPath(FlowPath... paths) {
        for (FlowPath path : paths) {
            path.getSegments().forEach(pathSegment -> {
                log.debug("Updating ISL for the path segment: {}", pathSegment);

                updateAvailableBandwidth(pathSegment.getSrcSwitch().getSwitchId(), pathSegment.getSrcPort(),
                        pathSegment.getDestSwitch().getSwitchId(), pathSegment.getDestPort());
            });
        }
    }

    private void updateAvailableBandwidth(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort) {
        long usedBandwidth = flowPathRepository.getUsedBandwidthBetweenEndpoints(srcSwitch, srcPort,
                dstSwitch, dstPort);
        log.debug("Updating ISL {}_{}-{}_{} with used bandwidth {}", srcSwitch, srcPort, dstSwitch, dstPort,
                usedBandwidth);
        islRepository.updateAvailableBandwidth(srcSwitch, srcPort, dstSwitch, dstPort, usedBandwidth);
    }

    protected void saveHistory(FlowRerouteFsm stateMachine, String flowId, FlowPath forwardPath, FlowPath reversePath) {
        FlowDumpData flowDumpData = HistoryMapper.INSTANCE.map(forwardPath.getFlow(), forwardPath, reversePath);
        flowDumpData.setDumpType(DumpType.STATE_BEFORE);
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowDumpData(flowDumpData)
                .flowHistoryData(FlowHistoryData.builder()
                        .action("Flow paths were removed")
                        .time(Instant.now())
                        .description(format("Flow paths %s/%s were removed",
                                forwardPath.getPathId(), reversePath.getPathId()))
                        .flowId(flowId)
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}
