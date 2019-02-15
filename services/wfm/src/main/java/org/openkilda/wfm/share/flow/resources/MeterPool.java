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

package org.openkilda.wfm.share.flow.resources;

import static java.lang.String.format;

import org.openkilda.model.FlowMeter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * The resource pool is responsible for meter de-/allocation.
 */
@Slf4j
public class MeterPool {
    private final TransactionManager transactionManager;
    private final FlowMeterRepository flowMeterRepository;
    private final SwitchRepository switchRepository;

    private final MeterId minMeterId;
    private final MeterId maxMeterId;

    public MeterPool(PersistenceManager persistenceManager, MeterId minMeterId, MeterId maxMeterId) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowMeterRepository = repositoryFactory.createFlowMeterRepository();
        switchRepository = repositoryFactory.createSwitchRepository();

        this.minMeterId = minMeterId;
        this.maxMeterId = maxMeterId;
    }

    /**
     * Allocates meter id.
     */
    public FlowMeter allocateMeter(SwitchId switchId, String flowId, PathId pathId) {
        return transactionManager.doInTransaction(() -> {
            MeterId availableMeterId = flowMeterRepository.findAvailableMeterId(switchId).orElse(minMeterId);
            if (availableMeterId.compareTo(maxMeterId) >= 0) {
                throw new ResourceNotAvailableException(format("No meter available for switch %s", switchId));
            }

            Switch theSwitch = switchRepository.findById(switchId)
                    .orElseThrow(() ->
                            new ResourceNotAvailableException(format("No switch for meter allocation: %s", switchId)));

            FlowMeter flowMeter = FlowMeter.builder()
                    .meterId(availableMeterId)
                    .flowId(flowId)
                    .pathId(pathId)
                    .theSwitch(theSwitch)
                    .build();
            flowMeterRepository.createOrUpdate(flowMeter);

            return flowMeter;
        });
    }

    /**
     * Deallocates meter id.
     */
    public void deallocateMeter(PathId pathId) {
        transactionManager.doInTransaction(() ->
                flowMeterRepository.findByPathId(pathId)
                        .ifPresent(flowMeterRepository::delete)
        );
    }
}
