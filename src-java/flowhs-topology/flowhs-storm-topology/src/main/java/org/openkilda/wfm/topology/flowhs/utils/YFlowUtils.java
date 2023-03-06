/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.utils;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import org.openkilda.messaging.command.yflow.SubFlowPathDto;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowPaths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class YFlowUtils {
    private final TransactionManager transactionManager;
    private final YFlowRepository yFlowRepository;

    public YFlowUtils(PersistenceManager persistenceManager) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
    }

    public YFlowPaths definePaths(String yFlowId, List<Long> ignoredPathsCookies) {
        return definePaths(collectPaths(yFlowId, ignoredPathsCookies));
    }

    public YFlowPaths definePaths(YFlow yFlow) {
        return definePaths(collectPaths(yFlow));
    }

    private YFlowPaths definePaths(List<FlowPath> flowPaths) {
        List<FlowPath> nonEmptyPaths = flowPaths.stream()
                .filter(fp -> !fp.getSegments().isEmpty()).collect(Collectors.toList());
        PathInfoData sharedPath = FlowPathMapper.INSTANCE.map(nonEmptyPaths.size() >= 2
                ? IntersectionComputer.calculatePathIntersectionFromSource(nonEmptyPaths) : emptyList());

        List<SubFlowPathDto> subFlowPaths = flowPaths.stream()
                .map(flowPath -> new SubFlowPathDto(flowPath.getFlowId(), FlowPathMapper.INSTANCE.map(flowPath)))
                .sorted(Comparator.comparing(SubFlowPathDto::getFlowId))
                .collect(Collectors.toList());

        return YFlowPaths.builder()
                .sharedPath(sharedPath)
                .subFlowPaths(subFlowPaths)
                .build();
    }

    private List<FlowPath> collectPaths(String yFlowId, List<Long> ignoredPathsCookies) {
        return transactionManager.doInTransaction(() -> {
            YFlow yFlow = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Y-flow %s not found", yFlowId)));
            return collectPaths(yFlow, ignoredPathsCookies);
        });
    }

    private List<FlowPath> collectPaths(YFlow yFlow) {
        return collectPaths(yFlow, Collections.emptyList());
    }

    private List<FlowPath> collectPaths(YFlow yFlow, List<Long> ignoredPathsCookies) {
        SwitchId sharedSwitchId = yFlow.getSharedEndpoint().getSwitchId();
        List<FlowPath> paths = new ArrayList<>();
        for (YSubFlow subFlow : yFlow.getSubFlows()) {
            Flow flow = subFlow.getFlow();
            FlowPath flowPath = flow.getPaths().stream()
                    .filter(path -> sharedSwitchId.equals(path.getSrcSwitchId())
                            && !path.isProtected()
                            && !ignoredPathsCookies.contains(path.getCookie().getValue()))
                    .findFirst()
                    .orElse(sharedSwitchId.equals(flow.getForwardPath().getSrcSwitchId()) ? flow.getForwardPath()
                            : flow.getReversePath());
            paths.add(flowPath);
        }
        return paths;
    }
}
