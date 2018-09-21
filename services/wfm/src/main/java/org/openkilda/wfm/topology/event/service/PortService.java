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

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;

@Slf4j
public class PortService {

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
     * Actions when port state is down and send reroute messages.
     *
     * @param port   port.
     * @param sender sender.
     */
    public void processWhenPortIsDown(Port port, Sender sender) {
        processWhenPortIsDown(port);

        String reason = String.format("Port %s_%s is %s",
                port.getSwitchId(), port.getPortNo(), port.getStatus());
        sender.sendRerouteAffectedFlowsMessage(port.getSwitchId(), port.getPortNo(), reason);
    }

    /**
     * Actions when port state is down.
     *
     * @param port port.
     */
    public void processWhenPortIsDown(Port port) {
        log.debug("Port {}_{} is in DOWN status", port.getSwitchId(), port.getPortNo());
        transactionManager.doInTransaction(() -> processDownPort(port));
    }

    private void processDownPort(Port port) {
        Collection<Isl> isls = islRepository.findBySrcEndpoint(port.getSwitchId(), port.getPortNo());

        for (Isl isl : isls) {
            log.info("Deactivate ISL {}", isl);
            isl.setActualStatus(IslStatus.INACTIVE);
            increaseIslCostOnPortDown(isl);

            Optional<Isl> reverseIsl = islRepository.findByEndpoints(isl.getDestSwitch().getSwitchId(),
                    isl.getDestPort(), isl.getSrcSwitch().getSwitchId(), isl.getSrcPort());
            reverseIsl.ifPresent(link -> {
                increaseIslCostOnPortDown(link);

                IslStatus status = IslService.getStatus(isl, link);
                isl.setStatus(status);
                link.setStatus(status);

                islRepository.createOrUpdate(link);
            });

            islRepository.createOrUpdate(isl);
        }
    }

    private void increaseIslCostOnPortDown(Isl isl) {
        if (isl.getCost() < islCostWhenPortDown) {
            isl.setCost(islCostWhenPortDown + isl.getCost());

            updateLinkPropsWithCost(isl);
        }
    }

    private void updateLinkPropsWithCost(Isl isl) {
        Collection<LinkProps> linkPropsDao = linkPropsRepository.findByEndpoints(
                isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                isl.getDestSwitch().getSwitchId(), isl.getDestPort());
        if (!linkPropsDao.isEmpty()) {
            LinkProps linkProps = linkPropsDao.iterator().next();
            if (linkProps.getCost() != null) {
                linkProps.setCost(isl.getCost());
                linkPropsRepository.createOrUpdate(linkProps);
            }
        }
    }
}
