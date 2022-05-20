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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.wfm.share.utils.PoolEntityAdapter;
import org.openkilda.wfm.topology.switchmanager.service.configs.LagPortOperationConfig;

import java.util.Optional;

public class LagPortPoolEntityAdapter implements PoolEntityAdapter {
    private final LagPortOperationConfig config;
    private final LagLogicalPortRepository repository;

    private final SwitchId switchId;

    public LagPortPoolEntityAdapter(
            LagPortOperationConfig config, LagLogicalPortRepository repository, SwitchId switchId) {
        this.config = config;
        this.repository = repository;
        this.switchId = switchId;
    }

    @Override
    public boolean allocateSpecificId(long entityId) {
        Optional<LagLogicalPort> existing = repository.findBySwitchIdAndPortNumber(switchId, (int) entityId);
        return ! existing.isPresent();
    }

    @Override
    public Optional<Long> allocateFirstInRange(long idMinimum, long idMaximum) {
        return repository.findUnassignedPortInRange(switchId, (int) idMinimum, (int) idMaximum)
                .map(Long::new);
    }

    @Override
    public String formatResourceNotAvailableMessage() {
        return String.format(
                "Unable to find any unassigned LAG logical port number for switch %s in range from %d to %d",
                switchId, config.getPoolConfig().getIdMinimum(), config.getPoolConfig().getIdMaximum());
    }
}
