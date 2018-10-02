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

package org.openkilda.wfm.topology.event.service;

import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Port;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PortService {

    private static final Logger logger = LoggerFactory.getLogger(SwitchService.class);

    private TransactionManager transactionManager;
    private IslRepository islRepository;
    private LinkPropsRepository linkPropsRepository;

    private int islCostWhenPortDown;

    public PortService(TransactionManager transactionManager, RepositoryFactory repositoryFactory,
                       int islCostWhenPortDown) {
        this.transactionManager = transactionManager;
        this.islRepository = repositoryFactory.createIslRepository();
        this.linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        this.islCostWhenPortDown = islCostWhenPortDown;
    }

    /**
     * Actions when port state is down.
     *
     * @param port port.
     * @return transaction was successful or not.
     */
    public boolean processWhenPortIsDown(Port port) {
        logger.info("Port {}_{} is in DOWN status", port.getSwitchId(), port.getPortNo());
        transactionManager.begin();
        try {
            processDownPort(port);
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            return false;
        }

        return true;
    }

    private void processDownPort(Port port) {
        List<Isl> isls = new ArrayList<>();
        islRepository.findByEndpoint(port.getSwitchId(), port.getPortNo())
                .forEach(isls::add);

        for (Isl isl : isls) {
            isl.setActualStatus(IslStatus.INACTIVE);

            islRepository.createOrUpdate(isl);

            setCost(isl);

            Isl reverseIsl = islRepository.findByEndpoints(isl.getDestSwitchId(), isl.getDestPort(),
                    isl.getSrcSwitchId(), isl.getSrcPort());
            setCost(reverseIsl);

            islRepository.updateStatus(isl);
        }

    }

    private void setCost(Isl isl) {
        if (islCostWhenPortDown <= isl.getCost()) {
            return;
        }

        isl.setCost(islCostWhenPortDown + isl.getCost());
        setCostInLinkProps(isl);
        islRepository.createOrUpdate(isl);
    }

    private void setCostInLinkProps(Isl isl) {
        LinkProps linkProps = linkPropsRepository.findByEndpoints(isl.getSrcSwitchId(), isl.getSrcPort(),
                isl.getDestSwitchId(), isl.getDestPort());
        if (linkProps != null) {
            Map<String, Object> properties = linkProps.getProperties();
            if (properties.containsKey("cost")) {
                properties.put("cost", (long) isl.getCost());
                linkProps.setProperties(properties);
                linkPropsRepository.createOrUpdate(linkProps);
            }
        }
    }
}
