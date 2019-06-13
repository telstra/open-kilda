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

package org.openkilda.wfm.topology.isllatency.service;

import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IslLatencyService {
    private TransactionManager transactionManager;
    private IslRepository islRepository;
    private SwitchRepository switchRepository;

    public IslLatencyService(TransactionManager transactionManager, RepositoryFactory repositoryFactory) {
        this.transactionManager = transactionManager;
        islRepository = repositoryFactory.createIslRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
    }

    /**
     * Update latency of isl by source and destination endpoint.
     *
     * @param srcSwitchId ID of source switch.
     * @param srcPort source port.
     * @param dstSwitchId ID of destination switch.
     * @param dstPort destination port.
     * @param latency latency to update
     *
     * @throws SwitchNotFoundException if src or dst switch is not found
     * @throws IslNotFoundException if isl is not found
     */
    public void updateIslLatency(
            SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort, long latency)
            throws SwitchNotFoundException, IslNotFoundException {
        transactionManager.doInTransaction(() -> {
            Switch srcSwitch = switchRepository.findById(srcSwitchId)
                    .orElseThrow(() -> new SwitchNotFoundException(srcSwitchId));
            Switch dstSwitch = switchRepository.findById(dstSwitchId)
                    .orElseThrow(() -> new SwitchNotFoundException(dstSwitchId));

            switchRepository.lockSwitches(srcSwitch, dstSwitch);
            Isl isl = islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort)
                    .orElseThrow(() -> new IslNotFoundException(srcSwitchId, srcPort, dstSwitchId, dstPort));
            isl.setLatency(latency);
            islRepository.createOrUpdate(isl);
        });
    }
}
