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
import static java.util.stream.Collectors.toList;

import org.openkilda.model.FlowMeter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionRequired;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The resource pool is responsible for meter de-/allocation.
 */
@Slf4j
public class MeterPool {
    private final TransactionManager transactionManager;
    private final FlowMeterRepository flowMeterRepository;

    private final MeterId minMeterId;
    private final MeterId maxMeterId;
    private final int poolSize;

    private Map<SwitchId, MeterId> nextMeters = new HashMap<>();

    public MeterPool(PersistenceManager persistenceManager, MeterId minMeterId, MeterId maxMeterId, int poolSize) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowMeterRepository = repositoryFactory.createFlowMeterRepository();

        this.minMeterId = minMeterId;
        this.maxMeterId = maxMeterId;
        this.poolSize = poolSize;
    }

    /**
     * Allocates a meter for the flow path.
     */
    @TransactionRequired
    public MeterId allocate(SwitchId switchId, String flowId, PathId pathId) {
        MeterId nextMeter = nextMeters.get(switchId);
        if (nextMeter != null && nextMeter.getValue() > 0) {
            if (nextMeter.compareTo(maxMeterId) <= 0 && !flowMeterRepository.exists(switchId, nextMeter)) {
                addMeter(flowId, pathId, switchId, nextMeter);
                nextMeters.put(switchId, new MeterId(nextMeter.getValue() + 1));
                return nextMeter;
            } else {
                nextMeters.remove(switchId);
            }
        }
        // The pool requires (re-)initialization.
        if (!nextMeters.containsKey(switchId)) {
            long numOfPools = (maxMeterId.getValue() - minMeterId.getValue()) / poolSize;
            if (numOfPools > 1) {
                long poolToTake = Math.abs(new Random().nextInt()) % numOfPools;
                Optional<MeterId> availableMeter = flowMeterRepository.findFirstUnassignedMeter(switchId,
                        new MeterId(minMeterId.getValue() + poolToTake * poolSize),
                        new MeterId(minMeterId.getValue() + (poolToTake + 1) * poolSize - 1));
                if (availableMeter.isPresent()) {
                    nextMeter = availableMeter.get();
                    addMeter(flowId, pathId, switchId, nextMeter);
                    nextMeters.put(switchId, new MeterId(nextMeter.getValue() + 1));
                    return nextMeter;
                }
            }
            // The pool requires full scan.
            nextMeter = new MeterId(-1);
            nextMeters.put(switchId, nextMeter);
        }
        if (nextMeter != null && nextMeter.getValue() == -1) {
            Optional<MeterId> availableMeter = flowMeterRepository.findFirstUnassignedMeter(switchId,
                    minMeterId, maxMeterId);
            if (availableMeter.isPresent()) {
                nextMeter = availableMeter.get();
                addMeter(flowId, pathId, switchId, nextMeter);
                nextMeters.put(switchId, new MeterId(nextMeter.getValue() + 1));
                return nextMeter;
            }
        }
        throw new ResourceNotAvailableException(format("No meter available for switch %s", switchId));
    }

    private void addMeter(String flowId, PathId pathId, SwitchId switchId, MeterId meterId) {
        FlowMeter flowMeter = FlowMeter.builder()
                .meterId(meterId)
                .switchId(switchId)
                .flowId(flowId)
                .pathId(pathId)
                .build();
        flowMeterRepository.add(flowMeter);
    }

    /**
     * Deallocates a meter(s) of the flow path(s).
     */
    public void deallocate(PathId... pathIds) {
        transactionManager.doInTransaction(() -> {
            List<FlowMeter> meters = Arrays.stream(pathIds)
                    .map(flowMeterRepository::findByPathId)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(toList());

            meters.forEach(flowMeterRepository::remove);
        });
    }

    /**
     * Deallocates a meter.
     */
    public void deallocate(SwitchId switchId, MeterId meterId) {
        transactionManager.doInTransaction(() -> {
            flowMeterRepository.findById(switchId, meterId)
                    .ifPresent(flowMeterRepository::remove);
        });
    }
}
