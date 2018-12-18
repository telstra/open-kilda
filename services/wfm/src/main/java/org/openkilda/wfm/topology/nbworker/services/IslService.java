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

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.share.mappers.IslMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class IslService {
    private static final Logger logger =
            LoggerFactory.getLogger(org.openkilda.wfm.topology.nbworker.services.IslService.class);

    private IslRepository islRepository;

    public IslService(RepositoryFactory repositoryFactory) {
        this.islRepository = repositoryFactory.createIslRepository();
    }

    /**
     * Gets all ISLs.
     *
     * @return List of ISLs
     */
    public List<IslInfoData> getAllIsls() {
        return islRepository.findAll().stream()
                .map(IslMapper.INSTANCE::map)
                .collect(Collectors.toList());
    }

    /**
     * Delete ISL.
     *
     * @param sourceSwitch ISL source switch
     * @param sourcePort ISL source port
     * @param destinationSwitch ISL destination switch
     * @param destinationPort ISL destination port
     *
     * @return True if ISL was deleted, False otherwise
     * @throws IslNotFoundException if ISL is not found
     * @throws IllegalIslStateException if ISL is in 'active' state
     */
    public boolean deleteIsl(SwitchId sourceSwitch, int sourcePort, SwitchId destinationSwitch, int destinationPort)
            throws IllegalIslStateException, IslNotFoundException {
        logger.info("Delete ISL with following parameters: "
                + "source switch '%s', source port '%d', destination switch '%s', destination port '%d'",
                sourceSwitch, sourcePort, destinationSwitch, destinationPort);

        Optional<Isl> isl = islRepository.findByEndpoints(sourceSwitch, sourcePort, destinationSwitch, destinationPort);

        if (!isl.isPresent()) {
            throw new IslNotFoundException(sourceSwitch, sourcePort, destinationSwitch, destinationPort);
        }

        if (isl.get().getStatus() == IslStatus.ACTIVE) {
            throw new IllegalIslStateException(sourceSwitch, sourcePort, destinationSwitch, destinationPort,
                    "ISL must NOT be in active state.");
        }

        islRepository.delete(isl.get());

        return !islRepository.findByEndpoints(sourceSwitch, sourcePort, destinationSwitch, destinationPort).isPresent();
    }
}
