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
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class IslService {

    private static final Logger logger = LoggerFactory.getLogger(IslService.class);

    private TransactionManager transactionManager;
    private IslRepository islRepository;
    private LinkPropsRepository linkPropsRepository;
    private SwitchRepository switchRepository;

    public IslService(TransactionManager transactionManager, RepositoryFactory repositoryFactory) {
        this.transactionManager = transactionManager;
        this.islRepository = repositoryFactory.createIslRepository();
        this.linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        this.switchRepository = repositoryFactory.createSwitchRepository();
    }

    /**
     * Create or update isl.
     *
     * @param isl isl.
     * @return transaction was successful or not.
     */
    public boolean createOrUpdateIsl(Isl isl) {
        logger.info("Create or update ISL {}_{}-{}_{}",
                isl.getSrcSwitchId(), isl.getSrcPort(), isl.getDestSwitchId(), isl.getDestPort());
        transactionManager.begin();
        try {
            processCreateOrUpdateIsl(isl);
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            return false;
        }

        transactionManager.begin();
        try {
            islRepository.setLinkPropsToIsl(isl);
            islRepository.updateIslBandwidth(isl.getSrcSwitchId(), isl.getSrcPort(),
                    isl.getDestSwitchId(), isl.getDestPort());
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            return false;
        }

        return true;
    }

    private void processCreateOrUpdateIsl(Isl isl) {
        Isl daoIsl = islRepository.findByEndpoints(isl.getSrcSwitchId(), isl.getSrcPort(),
                isl.getDestSwitchId(), isl.getDestPort());
        Isl reverseDaoIsl = islRepository.findByEndpoints(isl.getDestSwitchId(), isl.getDestPort(),
                isl.getSrcSwitchId(), isl.getSrcPort());

        daoIsl = createIsl(isl, daoIsl);
        reverseDaoIsl = createIsl(getReverseIsl(isl), reverseDaoIsl);

        daoIsl.setLatency(isl.getLatency());
        daoIsl.setSpeed(isl.getSpeed());
        daoIsl.setMaxBandwidth(isl.getAvailableBandwidth());
        daoIsl.setDefaultMaxBandwidth(isl.getAvailableBandwidth());
        daoIsl.setActualStatus(IslStatus.ACTIVE);

        islRepository.createOrUpdate(daoIsl);
        islRepository.createOrUpdate(reverseDaoIsl);

        islRepository.updateStatus(daoIsl);

        resolveConflicts(isl);
    }

    private Isl createIsl(Isl isl, Isl daoIsl) {
        if (daoIsl == null) {
            daoIsl = new Isl();

            Switch sourceSwitch = switchRepository.findBySwitchId(isl.getSrcSwitchId());
            if (sourceSwitch == null) {
                sourceSwitch = createSwitch(isl.getSrcSwitchId());
            }

            Switch destinationSwitch = switchRepository.findBySwitchId(isl.getDestSwitchId());
            if (destinationSwitch == null) {
                destinationSwitch = createSwitch(isl.getDestSwitchId());
            }

            daoIsl.setSrcSwitch(sourceSwitch);
            daoIsl.setSrcPort(isl.getSrcPort());

            daoIsl.setDestSwitch(destinationSwitch);
            daoIsl.setDestPort(isl.getDestPort());

            daoIsl.setStatus(IslStatus.INACTIVE);
            daoIsl.setActualStatus(IslStatus.INACTIVE);
            daoIsl.setLatency(-1);

            LinkProps linkProps = linkPropsRepository.findByEndpoints(isl.getSrcSwitchId(), isl.getSrcPort(),
                    isl.getDestSwitchId(), isl.getDestPort());
            if (linkProps != null && linkProps.getProperties().containsKey("cost")) {
                Long cost = (Long) linkProps.getProperties().get("cost");
                daoIsl.setCost(Math.toIntExact(cost));
            }

            Instant timestamp = Instant.now(Clock.systemUTC());
            daoIsl.setTimeCreate(timestamp);
            daoIsl.setTimeModify(timestamp);

        } else {
            Instant timestamp = Instant.now(Clock.systemUTC());
            daoIsl.setTimeModify(timestamp);
        }

        return daoIsl;
    }

    private void resolveConflicts(Isl isl) {
        List<Isl> isls = new ArrayList<>();
        isls.add(islRepository.findByEndpoints(isl.getSrcSwitchId(), isl.getSrcPort(),
                isl.getDestSwitchId(), isl.getDestPort()));
        isls.add(islRepository.findByEndpoints(isl.getDestSwitchId(), isl.getDestPort(),
                isl.getSrcSwitchId(), isl.getSrcPort()));

        islRepository.findByEndpoint(isl.getSrcSwitchId(), isl.getSrcPort())
                .forEach(isls::add);
        islRepository.findByEndpoint(isl.getDestSwitchId(), isl.getDestPort())
                .forEach(isls::add);

        for (Isl link: isls) {
            if (IslStatus.ACTIVE.equals(link.getActualStatus())) {
                logger.error("Detected ISL {}_{}-{}_{} conflict with {}_{}-{}_{}. Please contact dev team",
                        link.getSrcSwitchId(), link.getSrcPort(), link.getDestSwitchId(), link.getDestPort(),
                        isl.getSrcSwitchId(), isl.getSrcPort(), isl.getDestSwitchId(), isl.getDestPort());
            } else {
                logger.debug("Skip conflict ISL {}_{}-{}_{} deactivation due to its current status - {}",
                        link.getSrcSwitchId(), link.getSrcPort(), link.getDestSwitchId(), link.getDestPort(),
                        link.getActualStatus());
            }
        }
    }

    private Isl getReverseIsl(Isl isl) {
        Isl reverseIsl = new Isl();

        reverseIsl.setSrcSwitch(isl.getDestSwitch());
        reverseIsl.setSrcPort(isl.getDestPort());
        reverseIsl.setDestSwitch(isl.getSrcSwitch());
        reverseIsl.setDestPort(isl.getSrcPort());

        reverseIsl.setStatus(isl.getStatus());

        return reverseIsl;
    }

    private Switch createSwitch(SwitchId switchId) {
        Switch sw = new Switch();
        sw.setSwitchId(switchId);
        sw.setStatus(SwitchStatus.INACTIVE);
        return sw;
    }

    /**
     * Isl discovery failed.
     *
     * @param isl isl.
     * @return transaction was successful or not.
     */
    public boolean islDiscoveryFailed(Isl isl) {
        logger.info("ISL {}_{}-{}_{} discovery failed",
                isl.getSrcSwitchId(), isl.getSrcPort(), isl.getDestSwitchId(), isl.getDestPort());
        transactionManager.begin();
        try {
            processDiscoveryFailedIsl(isl);
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            return false;
        }
        return true;
    }

    private void processDiscoveryFailedIsl(Isl isl) {
        List<Isl> isls = new ArrayList<>();
        islRepository.findByEndpoint(isl.getSrcSwitchId(), isl.getSrcPort())
                .forEach(isls::add);

        for (Isl link : isls) {
            link.setActualStatus(IslStatus.MOVED);

            Isl reverseLink = islRepository.findByEndpoints(link.getDestSwitchId(), link.getDestPort(),
                    link.getSrcSwitchId(), link.getSrcPort());

            islRepository.createOrUpdate(link);
            islRepository.createOrUpdate(reverseLink);

            islRepository.updateStatus(link);
        }

    }
}
