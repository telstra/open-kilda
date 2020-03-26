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

package org.usermanagement.persistance;

import org.openkilda.config.DatabaseConfigurator;
import org.openkilda.constants.Status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.usermanagement.dao.repository.StatusRepository;

@Repository("configurator")
public class Configurator {

    private StatusRepository statusRepository;

    /**
     * Instantiates a new configurator.
     *
     * @param statusRepository
     *            the status repository.
     */
    public Configurator(@Autowired final DatabaseConfigurator databaseConfigurator,
            @Autowired final StatusRepository statusRepository) {
        this.statusRepository = statusRepository;
        init();
    }

    /**
     * Inits the status.
     */
    public void init() {
        loadStatus();
    }

    /**
     * Load status.
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void loadStatus() {
        statusRepository.findAll().stream().forEach((entity) -> {
            for (Status status : Status.values()) {
                if (status.getCode().equalsIgnoreCase(entity.getStatusCode())) {
                    status.setStatusEntity(entity);
                }
            }
        });
    }

}
