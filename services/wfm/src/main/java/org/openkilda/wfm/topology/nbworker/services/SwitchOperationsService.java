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

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.SwitchNotFoundException;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

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
        Optional<Switch> sw = switchRepository.findById(switchId);

        if (!sw.isPresent()) {
            throw new SwitchNotFoundException(switchId);
        }

        return sw.get();
    }
}
