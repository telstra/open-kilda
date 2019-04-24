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

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.DeactivateIslInfoData;
import org.openkilda.messaging.info.event.IslBfdFlagUpdated;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.DeleteLinkRequest;
import org.openkilda.messaging.nbtopology.request.GetLinksRequest;
import org.openkilda.messaging.nbtopology.request.LinkPropsDrop;
import org.openkilda.messaging.nbtopology.request.LinkPropsGet;
import org.openkilda.messaging.nbtopology.request.LinkPropsPut;
import org.openkilda.messaging.nbtopology.request.UpdateLinkEnableBfdRequest;
import org.openkilda.messaging.nbtopology.request.UpdateLinkUnderMaintenanceRequest;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.messaging.nbtopology.response.LinkPropsResponse;
import org.openkilda.model.FlowPair;
import org.openkilda.model.Isl;
import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchId;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.wfm.error.IllegalIslStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.share.mappers.IslMapper;
import org.openkilda.wfm.share.mappers.LinkPropsMapper;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.nbworker.services.FlowOperationsService;
import org.openkilda.wfm.topology.nbworker.services.ILinkOperationsServiceCarrier;
import org.openkilda.wfm.topology.nbworker.services.LinkOperationsService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LinkOperationsBolt extends PersistenceOperationsBolt implements ILinkOperationsServiceCarrier {
    private transient LinkOperationsService linkOperationsService;
    private transient FlowOperationsService flowOperationsService;

    private int islCostWhenUnderMaintenance;

    public LinkOperationsBolt(PersistenceManager persistenceManager, int islCostWhenUnderMaintenance) {
        super(persistenceManager);
        this.islCostWhenUnderMaintenance = islCostWhenUnderMaintenance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        this.linkOperationsService = new LinkOperationsService(this, repositoryFactory, transactionManager,
                islCostWhenUnderMaintenance);
        this.flowOperationsService = new FlowOperationsService(repositoryFactory, transactionManager);
    }

    @Override
    @SuppressWarnings("unchecked")
    List<InfoData> processRequest(Tuple tuple, BaseRequest request, String correlationId) {
        List<? extends InfoData> result = null;
        if (request instanceof GetLinksRequest) {
            result = getAllLinks((GetLinksRequest) request);
        } else if (request instanceof LinkPropsGet) {
            result = getLinkProps((LinkPropsGet) request);
        } else if (request instanceof LinkPropsPut) {
            result = Collections.singletonList(putLinkProps((LinkPropsPut) request));
        } else if (request instanceof LinkPropsDrop) {
            result = Collections.singletonList(dropLinkProps((LinkPropsDrop) request));
        } else if (request instanceof DeleteLinkRequest) {
            result = deleteLink((DeleteLinkRequest) request);
        } else if (request instanceof UpdateLinkUnderMaintenanceRequest) {
            result = updateLinkUnderMaintenanceFlag((UpdateLinkUnderMaintenanceRequest) request);
        } else if (request instanceof UpdateLinkEnableBfdRequest) {
            result = updateLinkEnableBfdFlag((UpdateLinkEnableBfdRequest) request);
        } else {
            unhandledInput(tuple);
        }

        return (List<InfoData>) result;
    }

    private List<IslInfoData> getAllLinks(GetLinksRequest request) {
        Integer srcPort = request.getSource().getPortNumber();
        SwitchId srcSwitch = request.getSource().getDatapath();
        Integer dstPort = request.getDestination().getPortNumber();
        SwitchId dstSwitch = request.getDestination().getDatapath();

        return linkOperationsService.getAllIsls(srcSwitch, srcPort, dstSwitch, dstPort).stream()
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

    private List<IslInfoData> deleteLink(DeleteLinkRequest request) {
        try {
            Collection<Isl> operationsResult = linkOperationsService.deleteIsl(request.getSrcSwitch(),
                    request.getSrcPort(), request.getDstSwitch(), request.getDstPort());
            List<IslInfoData> responseResult = operationsResult.stream()
                    .map(IslMapper.INSTANCE::map)
                    .collect(Collectors.toList());

            for (IslInfoData isl : responseResult) {
                DeactivateIslInfoData data = new DeactivateIslInfoData(isl.getSource(), isl.getDestination());
                getOutput().emit(StreamType.DISCO.toString(), getTuple(), new Values(data, getCorrelationId()));
            }

            return responseResult;
        } catch (IslNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "ISL was not found.");
        } catch (IllegalIslStateException e) {
            throw new MessageException(ErrorType.REQUEST_INVALID, e.getMessage(), "ISL is in illegal state.");
        }
    }

    private List<IslInfoData> updateLinkUnderMaintenanceFlag(UpdateLinkUnderMaintenanceRequest request) {
        SwitchId srcSwitch = request.getSource().getDatapath();
        Integer srcPort = request.getSource().getPortNumber();
        SwitchId dstSwitch = request.getDestination().getDatapath();
        Integer dstPort = request.getDestination().getPortNumber();
        boolean underMaintenance = request.isUnderMaintenance();
        boolean evacuate = request.isEvacuate();

        List<Isl> isl;
        try {
            isl = linkOperationsService.updateLinkUnderMaintenanceFlag(srcSwitch, srcPort,
                    dstSwitch, dstPort, underMaintenance);

            if (underMaintenance && evacuate) {
                flowOperationsService.getFlowsForLink(srcSwitch, srcPort, dstSwitch, dstPort).stream()
                        .map(FlowPair::getForward)
                        .map(UnidirectionalFlow::getFlowId).forEach(flowId -> {
                            FlowRerouteRequest rerouteRequest = new FlowRerouteRequest(flowId);
                            getOutput().emit(StreamType.REROUTE.toString(), getTuple(), new Values(rerouteRequest,
                                    getCorrelationId()));
                        });
            }

        } catch (IslNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "ISL was not found.");
        }

        return isl.stream()
                .map(IslMapper.INSTANCE::map)
                .collect(Collectors.toList());
    }

    private List<? extends InfoData> updateLinkEnableBfdFlag(UpdateLinkEnableBfdRequest request) {
        SwitchId srcSwitch = request.getSource().getDatapath();
        Integer srcPort = request.getSource().getPortNumber();
        SwitchId dstSwitch = request.getDestination().getDatapath();
        Integer dstPort = request.getDestination().getPortNumber();

        try {
            return linkOperationsService.updateLinkEnableBfdFlag(
                    srcSwitch, srcPort,
                    dstSwitch, dstPort, request.isEnableBfd())
                    .stream()
                    .map(IslMapper.INSTANCE::map)
                    .collect(Collectors.toList());

        } catch (IslNotFoundException e) {
            throw new MessageException(ErrorType.NOT_FOUND, e.getMessage(), "ISL was not found.");
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(new Fields("response", "correlationId"));
        declarer.declareStream(StreamType.REROUTE.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
        declarer.declareStream(StreamType.DISCO.toString(),
                new Fields(MessageEncoder.FIELD_ID_PAYLOAD, MessageEncoder.FIELD_ID_CONTEXT));
    }

    @Override
    public void islBfdFlagChanged(Isl isl) {
        IslInfoData islInfoData = IslMapper.INSTANCE.map(isl);
        IslBfdFlagUpdated data = IslBfdFlagUpdated.builder()
                .source(islInfoData.getSource())
                .destination(islInfoData.getDestination())
                .enableBfd(isl.isEnableBfd())
                .build();
        getOutput().emit(StreamType.DISCO.toString(), getTuple(), new Values(data, getCorrelationId()));
    }
}
