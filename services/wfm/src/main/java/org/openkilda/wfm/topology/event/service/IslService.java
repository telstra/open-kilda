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

import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class IslService {
    private TransactionManager transactionManager;
    private IslRepository islRepository;
    private LinkPropsRepository linkPropsRepository;
    private SwitchRepository switchRepository;
    private FlowPathRepository flowPathRepository;
    private FeatureTogglesRepository featureTogglesRepository;

    private int islCostWhenUnderMaintenance;

    public IslService(TransactionManager transactionManager, RepositoryFactory repositoryFactory,
                      int islCostWhenUnderMaintenance) {
        this.islCostWhenUnderMaintenance = islCostWhenUnderMaintenance;
        this.transactionManager = transactionManager;
        islRepository = repositoryFactory.createIslRepository();
        linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
    }

    /**
     * Create or update isl and send reroute message.
     *
     * @param isl    isl.
     * @param sender sender.
     */
    public void createOrUpdateIsl(Isl isl, Sender sender) {
        createOrUpdateIsl(isl);

        Optional<FeatureToggles> featureToggles = featureTogglesRepository.find();
        if (featureToggles.isPresent()
                && featureToggles.get().getFlowsRerouteOnIslDiscoveryEnabled() != null
                && featureToggles.get().getFlowsRerouteOnIslDiscoveryEnabled()) {

            String reason = String.format("Create or update ISL: %s_%d-%s_%d. ISL status: %s",
                    isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                    isl.getDestSwitch().getSwitchId(), isl.getDestPort(), isl.getStatus());
            sender.sendRerouteInactiveFlowsMessage(reason);
        } else {
            log.warn("Feature toggle 'flows_reroute_on_isl_discovery' is disabled");
        }
    }

    /**
     * Create or update isl.
     *
     * @param isl isl.
     */
    public void createOrUpdateIsl(Isl isl) {
        log.debug("Create or update ISL {}_{}-{}_{}",
                isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                isl.getDestSwitch().getSwitchId(), isl.getDestPort());
        transactionManager.doInTransaction(() -> processCreateOrUpdateIsl(isl));
    }

    private void processCreateOrUpdateIsl(Isl isl) {
        detectEndpointConflicts(isl);

        Instant timestamp = Instant.now();

        Isl daoIsl = islRepository.findByEndpoints(isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                isl.getDestSwitch().getSwitchId(), isl.getDestPort())
                .orElseGet(() -> isl.toBuilder()
                        .srcSwitch(switchRepository.reload(isl.getSrcSwitch()))
                        .destSwitch(switchRepository.reload(isl.getDestSwitch()))
                        .status(IslStatus.INACTIVE)
                        .timeCreate(timestamp)
                        .build());

        updateWith(daoIsl, isl);
        daoIsl.setTimeModify(timestamp);
        daoIsl.setActualStatus(IslStatus.ACTIVE);

        setLinkPropsData(daoIsl);

        Optional<Isl> foundReverseIsl = islRepository.findByEndpoints(isl.getDestSwitch().getSwitchId(),
                isl.getDestPort(), isl.getSrcSwitch().getSwitchId(), isl.getSrcPort());
        foundReverseIsl.ifPresent(reverseIsl -> {
            IslStatus status = getStatus(daoIsl, reverseIsl);
            daoIsl.setStatus(status);
            reverseIsl.setStatus(status);

            islRepository.createOrUpdate(reverseIsl);
        });

        long usedBandwidth = flowPathRepository.getUsedBandwidthBetweenEndpoints(
                isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                isl.getDestSwitch().getSwitchId(), isl.getDestPort());
        daoIsl.setAvailableBandwidth(daoIsl.getMaxBandwidth() - usedBandwidth);

        if ((daoIsl.getSrcSwitch().isUnderMaintenance() || daoIsl.getDestSwitch().isUnderMaintenance())
                && !daoIsl.isUnderMaintenance()) {
            daoIsl.setUnderMaintenance(true);
            daoIsl.setCost(daoIsl.getCost() + islCostWhenUnderMaintenance);
        }

        islRepository.createOrUpdate(daoIsl);

    }

    private void detectEndpointConflicts(Isl isl) {
        List<Isl> conflictIsls = new ArrayList<>();

        islRepository.findBySrcEndpoint(isl.getSrcSwitch().getSwitchId(), isl.getSrcPort())
                .forEach(link -> {
                    if (!link.getDestSwitch().getSwitchId().equals(isl.getDestSwitch().getSwitchId())
                            || link.getDestPort() != isl.getDestPort()) {
                        conflictIsls.add(link);
                    }
                });
        islRepository.findBySrcEndpoint(isl.getDestSwitch().getSwitchId(), isl.getDestPort())
                .forEach(link -> {
                    if (!link.getDestSwitch().getSwitchId().equals(isl.getSrcSwitch().getSwitchId())
                            || link.getDestPort() != isl.getSrcPort()) {
                        conflictIsls.add(link);
                    }
                });
        islRepository.findByDestEndpoint(isl.getSrcSwitch().getSwitchId(), isl.getSrcPort())
                .forEach(link -> {
                    if (!link.getSrcSwitch().getSwitchId().equals(isl.getDestSwitch().getSwitchId())
                            || link.getSrcPort() != isl.getDestPort()) {
                        conflictIsls.add(link);
                    }
                });
        islRepository.findByDestEndpoint(isl.getDestSwitch().getSwitchId(), isl.getDestPort())
                .forEach(link -> {
                    if (!link.getSrcSwitch().getSwitchId().equals(isl.getSrcSwitch().getSwitchId())
                            || link.getSrcPort() != isl.getSrcPort()) {
                        conflictIsls.add(link);
                    }
                });

        for (Isl link : conflictIsls) {
            if (IslStatus.ACTIVE.equals(link.getActualStatus())) {
                log.error("Detected ISL {}_{}-{}_{} conflict with {}_{}-{}_{}. Please contact dev team",
                        link.getSrcSwitch().getSwitchId(), link.getSrcPort(),
                        link.getDestSwitch().getSwitchId(), link.getDestPort(),
                        isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                        isl.getDestSwitch().getSwitchId(), isl.getDestPort());
            } else {
                log.debug("Skip conflict ISL {}_{}-{}_{} deactivation due to its current status - {}",
                        link.getSrcSwitch().getSwitchId(), link.getSrcPort(),
                        link.getDestSwitch().getSwitchId(), link.getDestPort(),
                        link.getActualStatus());
            }
        }
    }

    private void updateWith(Isl islToUpdate, Isl newIsl) {
        islToUpdate.setLatency(newIsl.getLatency());
        islToUpdate.setSpeed(newIsl.getSpeed());
        islToUpdate.setMaxBandwidth(newIsl.getAvailableBandwidth());
        islToUpdate.setDefaultMaxBandwidth(newIsl.getAvailableBandwidth());
    }

    private void setLinkPropsData(Isl isl) {
        Collection<LinkProps> linkPropsDao = linkPropsRepository.findByEndpoints(
                isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                isl.getDestSwitch().getSwitchId(), isl.getDestPort());
        if (!linkPropsDao.isEmpty()) {
            LinkProps linkProps = linkPropsDao.iterator().next();
            Integer cost = linkProps.getCost();
            if (cost != null) {
                isl.setCost(cost);
            }

            Long maxBandwidth = linkProps.getMaxBandwidth();
            if (maxBandwidth != null) {
                isl.setMaxBandwidth(maxBandwidth);
            }
        }
    }

    //TODO: move this into ISL pair entity in the future data model.
    static IslStatus getStatus(Isl forwardIsl, Isl reverseIsl) {
        if (forwardIsl.getActualStatus() == reverseIsl.getActualStatus()) {
            return forwardIsl.getActualStatus();
        } else if (forwardIsl.getActualStatus() == IslStatus.MOVED
                || reverseIsl.getActualStatus() == IslStatus.MOVED) {
            return IslStatus.MOVED;
        } else {
            return IslStatus.INACTIVE;
        }
    }

    /**
     * Isl discovery failed. Send reroute message.
     *
     * @param isl    isl.
     * @param sender sender.
     */
    public void islDiscoveryFailed(Isl isl, Sender sender) {
        if (islDiscoveryFailed(isl)) {
            String reason = String.format("ISL discovery failed. Endpoint: %s_%d. ISL status: %s",
                    isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(), isl.getStatus());
            sender.sendRerouteAffectedFlowsMessage(isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(), reason);
        }
    }

    /**
     * Isl discovery failed.
     *
     * @param isl isl.
     * @return ISL update happens flag
     */
    public boolean islDiscoveryFailed(Isl isl) {
        log.debug("ISL by source endpoint {}_{} discovery failed",
                isl.getSrcSwitch().getSwitchId(), isl.getSrcPort());
        return transactionManager.doInTransaction(() -> processDiscoveryFailedIsl(isl));
    }

    private boolean processDiscoveryFailedIsl(Isl isl) {
        IslStatus islStatus = IslStatus.MOVED == isl.getStatus() ? IslStatus.MOVED : IslStatus.INACTIVE;
        Collection<Isl> isls = islRepository.findBySrcEndpoint(isl.getSrcSwitch().getSwitchId(), isl.getSrcPort())
                .stream()
                .filter(link -> IslStatus.ACTIVE == link.getStatus()
                        || IslStatus.MOVED == islStatus)
                .collect(Collectors.toList());

        for (Isl link : isls) {
            log.info("Deactivate ISL {}", link);
            link.setActualStatus(islStatus);

            Optional<Isl> foundReverseLink = islRepository.findByEndpoints(link.getDestSwitch().getSwitchId(),
                    link.getDestPort(), link.getSrcSwitch().getSwitchId(), link.getSrcPort());
            foundReverseLink.ifPresent(reverseLink -> {
                IslStatus status = getStatus(link, reverseLink);
                link.setStatus(status);
                reverseLink.setStatus(status);

                islRepository.createOrUpdate(reverseLink);
            });

            islRepository.createOrUpdate(link);
        }
        return !isls.isEmpty();
    }
}
