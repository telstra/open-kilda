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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.LinkPropsDrop;
import org.openkilda.messaging.nbtopology.request.LinkPropsGet;
import org.openkilda.messaging.nbtopology.request.LinkPropsPut;
import org.openkilda.messaging.nbtopology.request.UpdateIslUnderMaintenanceRequest;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.messaging.nbtopology.response.LinkPropsResponse;
import org.openkilda.model.Isl;
import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.MessageException;
import org.openkilda.wfm.share.mappers.IslMapper;
import org.openkilda.wfm.share.mappers.LinkPropsMapper;
import org.openkilda.wfm.topology.nbworker.services.LinkOperationsService;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class LinkOperationsBolt extends PersistenceOperationsBolt {
    private transient LinkOperationsService linkOperationsService;

    public LinkOperationsBolt(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        this.linkOperationsService = new LinkOperationsService(repositoryFactory, transactionManager);
    }

    @Override
    @SuppressWarnings("unchecked")
    List<InfoData> processRequest(Tuple tuple, BaseRequest request) throws MessageException {
        List<? extends InfoData> result = null;
        if (request instanceof GetLinksRequest) {
            result = getAllLinks();
        } else if (request instanceof LinkPropsGet) {
            result = getLinkProps((LinkPropsGet) request);
        } else if (request instanceof LinkPropsPut) {
            result = Collections.singletonList(putLinkProps((LinkPropsPut) request));
        } else if (request instanceof LinkPropsDrop) {
            result = Collections.singletonList(dropLinkProps((LinkPropsDrop) request));
        } else if (request instanceof UpdateIslUnderMaintenanceRequest) {
            result = updateIslUnderMaintenanceFlag((UpdateIslUnderMaintenanceRequest) request);
        } else {
            unhandledInput(tuple);
        }

        return (List<InfoData>) result;
    }

    private List<IslInfoData> getAllLinks() {
        IslRepository islRepository = repositoryFactory.createIslRepository();
        return islRepository.findAll().stream()
                .map(IslMapper.INSTANCE::map)
                .collect(Collectors.toList());
    }

    private List<LinkPropsData> getLinkProps(LinkPropsGet request) {
        LinkPropsRepository linkPropsRepository = repositoryFactory.createLinkPropsRepository();

        Integer srcPort = request.getSource().getPortNumber();
        SwitchId srcSwitch = request.getSource().getDatapath();
        Integer dstPort = request.getDestination().getPortNumber();
        SwitchId dstSwitch = request.getDestination().getDatapath();

        Collection<LinkProps> linkProps = linkPropsRepository.findByEndpoints(srcSwitch, srcPort, dstSwitch, dstPort);
        return linkProps.stream()
                .map(LinkPropsMapper.INSTANCE::map)
                .map(LinkPropsData::new)
                .collect(Collectors.toList());
    }

    private LinkPropsResponse putLinkProps(LinkPropsPut request) {
        LinkPropsRepository linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        IslRepository islRepository = repositoryFactory.createIslRepository();

        try {
            LinkProps linkPropsToSet = LinkPropsMapper.INSTANCE.map(request.getLinkProps());

            LinkProps result = transactionManager.doInTransaction(() -> {
                Collection<LinkProps> existingLinkProps = linkPropsRepository.findByEndpoints(
                        linkPropsToSet.getSrcSwitchId(), linkPropsToSet.getSrcPort(),
                        linkPropsToSet.getDstSwitchId(), linkPropsToSet.getDstPort());

                LinkProps linkProps;
                if (!existingLinkProps.isEmpty()) {
                    linkProps = existingLinkProps.iterator().next();
                    if (linkPropsToSet.getCost() != null) {
                        linkProps.setCost(linkPropsToSet.getCost());
                    }
                    if (linkPropsToSet.getMaxBandwidth() != null) {
                        linkProps.setMaxBandwidth(linkPropsToSet.getMaxBandwidth());
                    }
                    linkProps.setTimeModify(Instant.now());
                } else {
                    linkProps = linkPropsToSet;
                    Instant timestamp = Instant.now();
                    linkProps.setTimeCreate(timestamp);
                    linkProps.setTimeModify(timestamp);
                }
                linkPropsRepository.createOrUpdate(linkProps);

                Optional<Isl> existingIsl = islRepository.findByEndpoints(
                        linkPropsToSet.getSrcSwitchId(), linkPropsToSet.getSrcPort(),
                        linkPropsToSet.getDstSwitchId(), linkPropsToSet.getDstPort());
                existingIsl.ifPresent(link -> {
                    if (linkPropsToSet.getCost() != null) {
                        link.setCost(linkPropsToSet.getCost());
                    }
                    if (linkPropsToSet.getMaxBandwidth() != null) {
                        link.setMaxBandwidth(linkPropsToSet.getMaxBandwidth());
                    }
                    link.setTimeModify(Instant.now());

                    islRepository.createOrUpdate(link);
                });

                return linkProps;
            });

            return new LinkPropsResponse(request, LinkPropsMapper.INSTANCE.map(result), null);
        } catch (Exception e) {
            log.error("Unhandled exception in put linkprops operation.", e);

            return new LinkPropsResponse(request, null, e.getMessage());
        }
    }

    private LinkPropsResponse dropLinkProps(LinkPropsDrop request) {
        LinkPropsRepository linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        IslRepository islRepository = repositoryFactory.createIslRepository();

        try {
            int srcPort = request.getPropsMask().getSource().getPortNumber();
            SwitchId srcSwitch = request.getPropsMask().getSource().getDatapath();
            int dstPort = request.getPropsMask().getDest().getPortNumber();
            SwitchId dstSwitch = request.getPropsMask().getDest().getDatapath();

            LinkProps result = transactionManager.doInTransaction(() -> {
                Collection<LinkProps> existingLinkProps = linkPropsRepository.findByEndpoints(
                        srcSwitch, srcPort, dstSwitch, dstPort);

                LinkProps linkProps = null;
                if (!existingLinkProps.isEmpty()) {
                    linkProps = existingLinkProps.iterator().next();
                    linkPropsRepository.delete(linkProps);
                }

                Optional<Isl> existingIsl = islRepository.findByEndpoints(
                        srcSwitch, srcPort, dstSwitch, dstPort);
                existingIsl.ifPresent(link -> {
                    link.setCost(0);
                    link.setMaxBandwidth(link.getDefaultMaxBandwidth());

                    islRepository.createOrUpdate(link);
                });

                return linkProps;
            });

            return new LinkPropsResponse(request, LinkPropsMapper.INSTANCE.map(result), null);
        } catch (Exception e) {
            log.error("Unhandled exception in drop linkprops operation.", e);

            return new LinkPropsResponse(request, null, e.getMessage());
        }
    }

    private List<IslInfoData> updateIslUnderMaintenanceFlag(UpdateIslUnderMaintenanceRequest request)
            throws MessageException {
        SwitchId srcSwitch = request.getSource().getDatapath();
        Integer srcPort = request.getSource().getPortNumber();
        SwitchId dstSwitch = request.getDestination().getDatapath();
        Integer dstPort = request.getDestination().getPortNumber();
        boolean underMaintenance = request.isUnderMaintenance();

        List<Isl> isl;
        try {
            isl = linkOperationsService.updateIslUnderMaintenanceFlag(srcSwitch, srcPort,
                    dstSwitch, dstPort, underMaintenance);
        } catch (IslNotFoundException e) {
            throw new MessageException(e.getMessage(), e);
        }

        return isl.stream()
                .map(IslMapper.INSTANCE::map)
                .collect(Collectors.toList());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(new Fields("response", "correlationId"));
    }
}
