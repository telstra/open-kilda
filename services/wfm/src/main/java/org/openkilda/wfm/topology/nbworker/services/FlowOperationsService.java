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

import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.error.IslNotFoundException;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class FlowOperationsService {

    private IslRepository islRepository;
    private FlowRepository flowRepository;

    public FlowOperationsService(RepositoryFactory repositoryFactory) {
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
    }

    /**
     * Return all flows for a particular link.
     *
     * @param srcSwitchId source switch id.
     * @param srcPort source port.
     * @param dstSwitchId destination switch id.
     * @param dstPort destination port.
     * @return all flows for a particular link.
     * @throws IslNotFoundException if there is no link with these parameters.
     */
    public Collection<FlowPair> getFlowIdsForLink(SwitchId srcSwitchId, Integer srcPort,
                                                SwitchId dstSwitchId, Integer dstPort)
            throws IslNotFoundException {

        if (!islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort).isPresent()) {
            throw new IslNotFoundException(srcSwitchId, srcPort, dstSwitchId, dstPort);
        }

        return flowRepository.findAllFlowPairsWithSegment(srcSwitchId, srcPort, dstSwitchId, dstPort);
    }
}
