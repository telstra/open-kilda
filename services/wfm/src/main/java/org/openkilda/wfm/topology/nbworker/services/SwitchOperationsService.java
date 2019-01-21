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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.mappers.SwitchMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SwitchOperationsService {

    private SwitchRepository switchRepository;

    public SwitchOperationsService(RepositoryFactory repositoryFactory) {
        this.switchRepository = repositoryFactory.createSwitchRepository();
    }

    /**
     * Get switch by switch id.
     *
     * @param switchId switch id.
     */
    public Switch getSwitch(SwitchId switchId) throws SwitchNotFoundException {
        return switchRepository.findById(switchId).orElseThrow(() -> new SwitchNotFoundException(switchId));
    }

    /**
     * Return all switches.
     *
     * @return all switches.
     */
    public List<SwitchInfoData> getAllSwitches() {
        return switchRepository.findAll().stream()
                .map(SwitchMapper.INSTANCE::map)
                .collect(Collectors.toList());
    }
}
