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
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
     * Set latency of isl by source endpoint.
     *
     * @param srcSwitchId ID of source switch.
     * @param srcPort source port.
     * @param latency latency to set/update
     *
     * @return updated ISL.
     *
     * @throws IslNotFoundException if there is no ISL with such source
     * @throws IllegalIslStateException if more than one ISL found
     */
    public Isl setIslLatencyBySourceEndpoint(SwitchId srcSwitchId, int srcPort, long latency)
            throws IslNotFoundException, IllegalIslStateException {
        return transactionManager.doInTransaction(() -> {
            Collection<Isl> isls = islRepository.findBySrcEndpoint(srcSwitchId, srcPort); // need to get dst endpoint

            validateIslsCount(srcSwitchId, srcPort, isls);
            Isl isl = isls.iterator().next();
            switchRepository.lockSwitches(isl.getSrcSwitch(), isl.getDestSwitch());

            // we can't just update Isl object because it was get without switch locks.
            islRepository.updateLatency(isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                    isl.getDestSwitch().getSwitchId(), isl.getDestPort(), latency);

            return isl;
        });
    }

    /**
     * Set latency of isl by source and destination endpoint.
     *
     * @param srcSwitchId ID of source switch.
     * @param srcPort source port.
     * @param dstSwitchId ID of destination switch.
     * @param dstPort destination port.
     * @param latency latency to set/update
     *
     * @return true if latency was updated.
     *
     * @throws SwitchNotFoundException if src or dst switch is not found
     */
    public boolean setIslLatencyBySourceAndDestinationEndpoint(
            SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort, long latency)
            throws SwitchNotFoundException {
        return transactionManager.doInTransaction(() -> {
            Switch srcSwitch = switchRepository.findById(srcSwitchId)
                    .orElseThrow(() -> new SwitchNotFoundException(srcSwitchId));
            Switch dstSwitch = switchRepository.findById(dstSwitchId)
                    .orElseThrow(() -> new SwitchNotFoundException(dstSwitchId));
            switchRepository.lockSwitches(srcSwitch, dstSwitch);
            return islRepository.updateLatency(srcSwitchId, srcPort, dstSwitchId, dstPort, latency);
        });
    }

    /**
     * Copy latency from reverse Isl to forward.
     *
     * @param srcSwitchId ID of source switch.
     * @param srcPort source port.
     * @param dstSwitchId ID of destination switch.
     * @param dstPort destination port.
     *
     * @return updated ISL.
     *
     * @throws IslNotFoundException if no ISL found (forward or reverse)
     */
    public Isl copyLatencyFromReverseIsl(
            SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort)
            throws IslNotFoundException {
        return transactionManager.doInTransaction(() -> {
            Isl reverseIsl = islRepository.findByEndpoints(dstSwitchId, dstPort, srcSwitchId, srcPort)
                    .orElseThrow(() -> new IslNotFoundException(dstSwitchId, dstPort, srcSwitchId, srcPort));

            switchRepository.lockSwitches(reverseIsl.getSrcSwitch(), reverseIsl.getDestSwitch());

            Isl forwardIsl = islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort)
                    .orElseThrow(() -> new IslNotFoundException(srcSwitchId, srcPort, dstSwitchId, dstPort));

            forwardIsl.setLatency(reverseIsl.getLatency());
            islRepository.createOrUpdate(forwardIsl);
            return forwardIsl;
        });
    }

    /**
     * Get isl by source endpoint.
     *
     * @param srcSwitchId ID of source switch.
     * @param srcPort source port.
     *
     * @return ISL.
     */
    public Isl getIsl(SwitchId srcSwitchId, int srcPort) throws IslNotFoundException, IllegalIslStateException {
        Collection<Isl> isls = islRepository.findBySrcEndpoint(srcSwitchId, srcPort);
        validateIslsCount(srcSwitchId, srcPort, isls);
        return isls.iterator().next();
    }

    /**
     * Get isl by source and destination endpoints.
     *
     * @param srcSwitchId ID of source switch.
     * @param srcPort source port.
     * @param dstSwitchId ID of destination switch.
     * @param dstPort destination port.
     *
     * @return ISL.
     */
    public Isl getIsl(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort)
            throws IslNotFoundException {
        return islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort)
                .orElseThrow(() -> new IslNotFoundException(srcSwitchId, srcPort, dstSwitchId, dstPort));
    }

    private void validateIslsCount(SwitchId srcSwitchId, int srcPort, Collection<Isl> isls)
            throws IslNotFoundException, IllegalIslStateException {
        if (isls == null || isls.isEmpty()) {
            throw new IslNotFoundException(srcSwitchId, srcPort);
        }
        if (isls.size() > 1) {
            List<String> dstEndpoints = isls.stream()
                    .map(isl -> String.format("%s_%d", isl.getDestSwitch(), isl.getDestPort()))
                    .collect(Collectors.toList());
            String message = String.format("There is more than one (%d) ISL with source in %s_%d. "
                    + "Destination endpoints: %s.", isls.size(), srcSwitchId, srcPort, dstEndpoints);
            throw new IllegalIslStateException(srcSwitchId, srcPort, message);
        }
    }
}
