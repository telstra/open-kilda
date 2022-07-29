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

package org.openkilda.wfm.share.flow.resources;

import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.wfm.share.utils.PoolEntityAdapter;
import org.openkilda.wfm.share.utils.PoolManager;
import org.openkilda.wfm.share.utils.PoolManager.PoolConfig;

import java.util.Optional;

public class MeterIdPoolEntityAdapter implements PoolEntityAdapter {
    private final PoolManager.PoolConfig config;
    private final SwitchId switchId;

    private final FlowMeterRepository flowMeterRepository;

    public MeterIdPoolEntityAdapter(FlowMeterRepository flowMeterRepository, PoolConfig config, SwitchId switchId) {
        this.config = config;
        this.switchId = switchId;
        this.flowMeterRepository = flowMeterRepository;
    }

    @Override
    public boolean allocateSpecificId(long entityId) {
        MeterId entity = new MeterId(entityId);
        return ! flowMeterRepository.exists(switchId, entity);
    }

    @Override
    public Optional<Long> allocateFirstInRange(long idMinimum, long idMaximum) {
        MeterId first = new MeterId(idMinimum);
        MeterId last = new MeterId(idMaximum);
        return flowMeterRepository.findFirstUnassignedMeter(switchId, first, last)
                .map(MeterId::getValue);
    }

    @Override
    public String formatResourceNotAvailableMessage() {
        return String.format(
                "Unable to find any unassigned MeterId for switch %s in range from %d to %d",
                switchId, config.getIdMinimum(), config.getIdMaximum());
    }
}
