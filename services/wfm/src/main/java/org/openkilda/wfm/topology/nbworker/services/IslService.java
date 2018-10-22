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
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.share.mappers.IslMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class IslService {
    private static final Logger logger =
            LoggerFactory.getLogger(org.openkilda.wfm.topology.nbworker.services.IslService.class);

    private TransactionManager transactionManager;
    private IslRepository islRepository;

    public IslService(TransactionManager transactionManager, RepositoryFactory repositoryFactory) {
        this.transactionManager = transactionManager;
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
     * @return True if operation was success, False otherwise.
     *
     * @throws IslNotFoundException if ISL is not found
     * @throws IllegalIslStateException if ISL is in 'active' state
     */
    public boolean deleteIsl(String sourceSwitch, int sourcePort, String destinationSwitch, int destinationPort)
            throws IllegalIslStateException, IslNotFoundException {
        logger.info("Delete ISL with following parameters: "
                + "source switch '%s', source port '%d', destination switch '%s', destination port '%d'",
                sourceSwitch, sourcePort, destinationSwitch, destinationPort);
        transactionManager.begin();
        try {
            processDeleteIsl(sourceSwitch, sourcePort, destinationSwitch, destinationPort);
            transactionManager.commit();
        } catch (IllegalIslStateException | IslNotFoundException e) {
            transactionManager.rollback();
            throw e;
        } catch (Exception e) {
            transactionManager.rollback();
            return false;
        }
        return true;
    }

    private void processDeleteIsl(String sourceSwitch, int sourcePort, String destinationSwitch, int destinationPort)
            throws IllegalIslStateException, IslNotFoundException {
        SwitchId sourceSwitchId;
        SwitchId destinationSwitchId;

        try {
            sourceSwitchId = new SwitchId(sourceSwitch);
            destinationSwitchId = new SwitchId(destinationSwitch);
        } catch (IllegalArgumentException e) {
            throw new IslNotFoundException(sourceSwitch, sourcePort, destinationSwitch, destinationPort);
        }

        Isl isl = islRepository.findByEndpoints(sourceSwitchId, sourcePort, destinationSwitchId, destinationPort);

        if (isl == null) {
            throw new IslNotFoundException(sourceSwitch, sourcePort, destinationSwitch, destinationPort);
        }

        if (isl.getStatus() == IslStatus.ACTIVE) {
            throw new IllegalIslStateException(sourceSwitch, sourcePort, destinationSwitch, destinationPort,
                    "ISL must not be in active state.");
        }

        islRepository.delete(isl);
    }
}
