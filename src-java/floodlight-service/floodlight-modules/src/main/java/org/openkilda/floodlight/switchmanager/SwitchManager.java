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

package org.openkilda.floodlight.switchmanager;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.isOvs;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.converter.OfPortDescConverter;
import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.OfInstallException;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.switchmanager.web.SwitchManagerWebRoutable;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFPortConfig;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortMod;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.Match.Builder;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFMetadata;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Created by jonv on 29/3/17.
 */
public class SwitchManager implements IFloodlightModule, IFloodlightService, ISwitchManager, IOFMessageListener {
    private static final Logger logger = LoggerFactory.getLogger(SwitchManager.class);

    public static final int VERIFICATION_RULE_PRIORITY = FlowModUtils.PRIORITY_MAX - 1000;
    public static final int FLOW_PRIORITY = FlowModUtils.PRIORITY_HIGH;
    public static final int FLOW_LOOP_PRIORITY = FLOW_PRIORITY + 100;
    public static final int MIRROR_FLOW_PRIORITY = FLOW_PRIORITY + 50;
    public static final int ISL_EGRESS_VXLAN_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 2;
    public static final int ISL_TRANSIT_VXLAN_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 3;
    public static final int INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 2;
    public static final int ISL_EGRESS_VLAN_RULE_PRIORITY_MULTITABLE = FLOW_PRIORITY - 5;

    public static final int SERVER_42_FLOW_RTT_INPUT_PRIORITY = INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE;

    public static final int SERVER_42_ISL_RTT_INPUT_PRIORITY = VERIFICATION_RULE_PRIORITY;

    public static final int LLDP_INPUT_CUSTOMER_PRIORITY = FLOW_PRIORITY - 1;

    public static final int ARP_INPUT_CUSTOMER_PRIORITY = FLOW_PRIORITY - 1;

    public static final int SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY_OFFSET = -10;
    public static final int SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY_OFFSET = 10;

    public static final IPv4Address STUB_VXLAN_IPV4_SRC = IPv4Address.of("127.0.0.1");
    public static final IPv4Address STUB_VXLAN_IPV4_DST = IPv4Address.of("127.0.0.2");
    public static final int STUB_VXLAN_UDP_SRC = 4500;
    public static final int SERVER_42_FLOW_RTT_FORWARD_UDP_PORT = 4700;
    public static final int VXLAN_UDP_DST = 4789;

    public static final int INPUT_TABLE_ID = 0;
    public static final int PRE_INGRESS_TABLE_ID = 1;
    public static final int INGRESS_TABLE_ID = 2;
    public static final int POST_INGRESS_TABLE_ID = 3;
    public static final int EGRESS_TABLE_ID = 4;
    public static final int TRANSIT_TABLE_ID = 5;

    public static final int NOVIFLOW_TIMESTAMP_SIZE_IN_BITS = 64;

    // This is invalid VID mask - it cut of highest bit that indicate presence of VLAN tag on package. But valid mask
    // 0x1FFF lead to rule reject during install attempt on accton based switches.
    private static short OF10_VLAN_MASK = 0x0FFF;

    private IOFSwitchService ofSwitchService;
    private IKafkaProducerService producerService;

    private SwitchManagerConfig config;

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return ImmutableList.of(
                ISwitchManager.class,
                SwitchTrackingService.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return ImmutableMap.<Class<? extends IFloodlightService>, IFloodlightService>builder()
                .put(ISwitchManager.class, this)
                .put(SwitchTrackingService.class, new SwitchTrackingService())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableList.of(
                IFloodlightProviderService.class,
                IOFSwitchService.class,
                IRestApiService.class,
                KildaCore.class,
                KafkaUtilityService.class,
                IKafkaProducerService.class,
                FeatureDetectorService.class,
                IPathVerificationService.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        ofSwitchService = context.getServiceImpl(IOFSwitchService.class);
        producerService = context.getServiceImpl(IKafkaProducerService.class);
        FloodlightModuleConfigurationProvider provider = FloodlightModuleConfigurationProvider.of(context, this);
        config = provider.getConfiguration(SwitchManagerConfig.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startUp(FloodlightModuleContext context) {
        logger.info("Module {} - start up", SwitchTrackingService.class.getName());
        context.getServiceImpl(SwitchTrackingService.class).setup(context);

        context.getServiceImpl(IFloodlightProviderService.class).addOFMessageListener(OFType.ERROR, this);
        context.getServiceImpl(IRestApiService.class).addRestletRoutable(new SwitchManagerWebRoutable());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NewCorrelationContextRequired
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        logger.debug("OF_ERROR: {}", msg);
        // TODO: track xid for flow id
        if (OFType.ERROR.equals(msg.getType())) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.INTERNAL_ERROR, ((OFErrorMsg) msg).getErrType().toString(), null),
                    System.currentTimeMillis(), CorrelationContext.getId(), Destination.WFM_TRANSACTION);
            // TODO: Most/all commands are flow related, but not all. 'kilda.flow' might
            // not be the best place to send a generic error.
            producerService.sendMessageAndTrackWithZk("kilda.flow", error);
        }
        return Command.CONTINUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "KildaSwitchManager";
    }

    // ISwitchManager Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        logger.trace("isCallbackOrderingPrereq for {} : {}", type, name);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        logger.trace("isCallbackOrderingPostreq for {} : {}", type, name);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OFFlowStatsEntry> dumpFlowTable(final DatapathId dpid) throws SwitchNotFoundException {
        List<OFFlowStatsEntry> entries = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);

        OFFactory ofFactory = sw.getOFFactory();
        OFFlowStatsRequest flowRequest = ofFactory.buildFlowStatsRequest()
                .setOutGroup(OFGroup.ANY)
                .setCookieMask(U64.ZERO)
                .build();

        try {
            Future<List<OFFlowStatsReply>> future = sw.writeStatsRequest(flowRequest);
            List<OFFlowStatsReply> values = future.get(10, TimeUnit.SECONDS);
            if (values != null) {
                entries = values.stream()
                        .map(OFFlowStatsReply::getEntries)
                        .flatMap(List::stream)
                        .collect(toList());
            }
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Could not get flow stats for {}.", dpid, e);
            throw new SwitchNotFoundException(dpid);
        } catch (InterruptedException e) {
            logger.error("Could not get flow stats for {}.", dpid, e);
            Thread.currentThread().interrupt();
            throw new SwitchNotFoundException(dpid);
        }

        return entries;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OFMeterConfig> dumpMeters(final DatapathId dpid) throws SwitchOperationException {
        List<OFMeterConfig> result = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);
        if (sw == null) {
            throw new IllegalArgumentException(format("Switch %s was not found", dpid));
        }

        verifySwitchSupportsMeters(sw);

        OFFactory ofFactory = sw.getOFFactory();
        OFMeterConfigStatsRequest meterRequest = ofFactory.buildMeterConfigStatsRequest()
                .setMeterId(0xffffffff)
                .build();

        try {
            ListenableFuture<List<OFMeterConfigStatsReply>> future = sw.writeStatsRequest(meterRequest);
            List<OFMeterConfigStatsReply> values = future.get(10, TimeUnit.SECONDS);
            if (values != null) {
                result = values.stream()
                        .map(OFMeterConfigStatsReply::getEntries)
                        .flatMap(List::stream)
                        .collect(toList());
            }
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Could not get meter config stats for {}.", dpid, e);
        } catch (InterruptedException e) {
            logger.error("Could not get meter config stats for {}.", dpid, e);
            Thread.currentThread().interrupt();
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OFMeterConfig dumpMeterById(final DatapathId dpid, final long meterId) throws SwitchOperationException {
        OFMeterConfig meterConfig = null;
        IOFSwitch sw = lookupSwitch(dpid);
        if (sw == null) {
            throw new IllegalArgumentException(format("Switch %s was not found", dpid));
        }

        verifySwitchSupportsMeters(sw);
        OFFactory ofFactory = sw.getOFFactory();
        OFMeterConfigStatsRequest meterRequest = ofFactory.buildMeterConfigStatsRequest()
                .setMeterId(meterId)
                .build();

        try {
            ListenableFuture<List<OFMeterConfigStatsReply>> future = sw.writeStatsRequest(meterRequest);
            List<OFMeterConfigStatsReply> values = future.get(10, TimeUnit.SECONDS);
            if (values != null) {
                List<OFMeterConfig> result = values.stream()
                        .map(OFMeterConfigStatsReply::getEntries)
                        .flatMap(List::stream)
                        .collect(toList());
                meterConfig = result.size() >= 1 ? result.get(0) : null;
            }
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Could not get meter config stats for {}.", dpid, e);
        } catch (InterruptedException e) {
            logger.error("Could not get meter config stats for {}.", dpid, e);
            Thread.currentThread().interrupt();
            throw new SwitchNotFoundException(dpid);
        }

        return meterConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void modifyMeterForFlow(DatapathId dpid, long meterId, long bandwidth) throws SwitchOperationException {
        if (!MeterId.isMeterIdOfFlowRule(meterId)) {
            throw new InvalidMeterIdException(dpid,
                    format("Could not modify meter '%d' on switch '%s'. Meter Id is invalid. Valid meter id range is "
                            + "[%d, %d]", meterId, dpid, MeterId.MIN_FLOW_METER_ID, MeterId.MAX_FLOW_METER_ID));
        }
        IOFSwitch sw = lookupSwitch(dpid);
        verifySwitchSupportsMeters(sw);

        long burstSize = Meter.calculateBurstSize(bandwidth, config.getFlowMeterMinBurstSizeInKbits(),
                config.getFlowMeterBurstCoefficient(), sw.getSwitchDescription().getManufacturerDescription(),
                sw.getSwitchDescription().getSoftwareDescription());

        Set<OFMeterFlags> flags = Arrays.stream(Meter.getMeterKbpsFlags())
                .map(OFMeterFlags::valueOf)
                .collect(Collectors.toSet());

        modifyMeter(sw, bandwidth, burstSize, meterId, flags);
    }


    @Override
    public Map<DatapathId, IOFSwitch> getAllSwitchMap(boolean visible) {
        return ofSwitchService.getAllSwitchMap().entrySet()
                .stream()
                .filter(e -> visible == e.getValue().getStatus().isVisible())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteMeter(final DatapathId dpid, final long meterId) throws SwitchOperationException {
        if (meterId > 0L) {
            IOFSwitch sw = lookupSwitch(dpid);
            verifySwitchSupportsMeters(sw);
            buildAndDeleteMeter(sw, dpid, meterId);

            // to ensure that we have completed meter deletion, because we might have remove/create meter in a row
            sendBarrierRequest(sw);
        } else {
            throw new InvalidMeterIdException(dpid, "Meter id must be positive.");
        }
    }

    @Override
    public List<Long> deleteRulesByCriteria(DatapathId dpid, DeleteRulesCriteria... criteria)
            throws SwitchOperationException {
        List<OFFlowStatsEntry> flowStatsBefore = dumpFlowTable(dpid);

        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();

        for (DeleteRulesCriteria criteriaEntry : criteria) {
            OFFlowDelete dropFlowDelete = buildFlowDeleteByCriteria(ofFactory, criteriaEntry);
            logger.info("Rules by criteria {} are to be removed from switch {}.", criteria, dpid);

            pushFlow(sw, "--DeleteFlow--", dropFlowDelete);
        }

        // Wait for OFFlowDelete to be processed.
        sendBarrierRequest(sw);

        List<OFFlowStatsEntry> flowStatsAfter = dumpFlowTable(dpid);
        Set<Long> cookiesAfter = flowStatsAfter.stream()
                .map(entry -> entry.getCookie().getValue())
                .collect(Collectors.toSet());

        return flowStatsBefore.stream()
                .map(entry -> entry.getCookie().getValue())
                .filter(cookie -> !cookiesAfter.contains(cookie))
                .peek(cookie -> logger.info("Rule with cookie {} has been removed from switch {}.", cookie, dpid))
                .collect(toList());
    }

    @Override
    public List<OFGroupDescStatsEntry> dumpGroups(DatapathId dpid) throws SwitchOperationException {
        IOFSwitch sw = lookupSwitch(dpid);
        return dumpGroups(sw);
    }

    private List<OFGroupDescStatsEntry> dumpGroups(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();
        OFGroupDescStatsRequest groupRequest = ofFactory.buildGroupDescStatsRequest().build();

        List<OFGroupDescStatsReply> replies;

        try {
            ListenableFuture<List<OFGroupDescStatsReply>> future = sw.writeStatsRequest(groupRequest);
            replies = future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Could not dump groups on switch {}.", sw.getId(), e);
            return Collections.emptyList();
        } catch (InterruptedException e) {
            logger.error("Could not dump groups on switch {}.", sw.getId(), e);
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }

        return replies.stream()
                .map(OFGroupDescStatsReply::getEntries)
                .flatMap(List::stream)
                .collect(toList());
    }

    private void verifySwitchSupportsMeters(IOFSwitch sw) throws UnsupportedSwitchOperationException {
        if (!config.isOvsMetersEnabled() && isOvs(sw)) {
            throw new UnsupportedSwitchOperationException(sw.getId(),
                    format("Meters are not supported on OVS switch %s", sw.getId()));
        }

        if (sw.getOFFactory().getVersion().compareTo(OF_12) <= 0) {
            throw new UnsupportedSwitchOperationException(sw.getId(),
                    format("Meters are not supported on switch %s because of OF version %s",
                            sw.getId(), sw.getOFFactory().getVersion()));
        }
    }

    private void modifyMeter(IOFSwitch sw, long bandwidth, long burstSize, long meterId, Set<OFMeterFlags> flags)
            throws OfInstallException {
        logger.info("Updating meter {} on Switch {}", meterId, sw.getId());

        OFMeterMod meterMod = buildMeterMode(sw, OFMeterModCommand.MODIFY, bandwidth, burstSize, meterId, flags);

        pushFlow(sw, "--ModifyMeter--", meterMod);
    }

    private OFMeterMod buildMeterMode(IOFSwitch sw, OFMeterModCommand command, long bandwidth, long burstSize,
                                      long meterId, Set<OFMeterFlags> flags) {
        OFFactory ofFactory = sw.getOFFactory();

        OFMeterBandDrop.Builder bandBuilder = ofFactory.meterBands()
                .buildDrop()
                .setRate(bandwidth)
                .setBurstSize(burstSize);

        OFMeterMod.Builder meterModBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterId)
                .setCommand(command)
                .setFlags(flags);

        if (sw.getOFFactory().getVersion().compareTo(OF_13) > 0) {
            meterModBuilder.setBands(singletonList(bandBuilder.build()));
        } else {
            meterModBuilder.setMeters(singletonList(bandBuilder.build()));
        }

        return meterModBuilder.build();
    }

    private void buildAndDeleteMeter(IOFSwitch sw, final DatapathId dpid, final long meterId)
            throws OfInstallException {
        logger.debug("deleting meter {} from switch {}", meterId, dpid);

        OFFactory ofFactory = sw.getOFFactory();

        OFMeterMod.Builder meterDeleteBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterId)
                .setCommand(OFMeterModCommand.DELETE);

        if (sw.getOFFactory().getVersion().compareTo(OF_13) > 0) {
            meterDeleteBuilder.setBands(emptyList());
        } else {
            meterDeleteBuilder.setMeters(emptyList());
        }

        OFMeterMod meterDelete = meterDeleteBuilder.build();

        pushFlow(sw, "--DeleteMeter--", meterDelete);
    }

    private OFFlowDelete buildFlowDeleteByCriteria(OFFactory ofFactory, DeleteRulesCriteria criteria) {
        OFFlowDelete.Builder builder = ofFactory.buildFlowDelete();
        if (criteria.getCookie() != null) {
            builder.setCookie(U64.of(criteria.getCookie()));
            builder.setCookieMask(U64.NO_MASK);
        }
        Match.Builder matchBuilder = ofFactory.buildMatch();
        if (criteria.getMetadataValue() != null && criteria.getMetadataMask() != null) {
            matchBuilder.setMasked(MatchField.METADATA, OFMetadata.of(U64.of(criteria.getMetadataValue())),
                    OFMetadata.of(U64.of(criteria.getMetadataMask())));
        }

        if (criteria.getInPort() != null) {
            // Match either In Port or both Port & Vlan criteria.
            addMatchFlowToBuilder(matchBuilder, ofFactory, criteria.getInPort(),
                    Optional.ofNullable(criteria.getEncapsulationId()).orElse(0), criteria.getEncapsulationType());
        } else if (criteria.getEncapsulationId() != null) {
            // Match In Vlan criterion if In Port is not specified
            switch (criteria.getEncapsulationType()) {
                case TRANSIT_VLAN:
                    matchVlan(ofFactory, matchBuilder, criteria.getEncapsulationId());
                    break;
                case VXLAN:
                    matchVxlan(ofFactory, matchBuilder, criteria.getEncapsulationId());
                    break;
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown encapsulation type: %s", criteria.getEncapsulationType()));
            }
        }
        Match match = matchBuilder.build();
        if (!match.equals(ofFactory.buildMatch().build())) {
            // we should not set empty match
            builder.setMatch(matchBuilder.build());
        }

        if (criteria.getPriority() != null) {
            // Match Priority criterion.
            builder.setPriority(criteria.getPriority());
        }

        if (criteria.getOutPort() != null) {
            // Match only Out Vlan criterion.
            builder.setOutPort(OFPort.of(criteria.getOutPort()));
        }

        return builder.setTableId(TableId.ALL).build();
    }

    private OFBarrierReply sendBarrierRequest(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();
        OFBarrierRequest barrierRequest = ofFactory.buildBarrierRequest().build();

        OFBarrierReply result = null;
        try {
            ListenableFuture<OFBarrierReply> future = sw.writeRequest(barrierRequest);
            result = future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Could not get a barrier reply for {}.", sw.getId(), e);
        } catch (InterruptedException e) {
            logger.error("Could not get a barrier reply for {}.", sw.getId(), e);
            Thread.currentThread().interrupt();
        }
        return result;
    }

    private void addMatchFlowToBuilder(Builder builder, OFFactory ofFactory, int inputPort, int tunnelId,
                                       FlowEncapsulationType encapsulationType) {
        builder.setExact(MatchField.IN_PORT, OFPort.of(inputPort));
        // NOTE: vlan of 0 means match on port on not VLAN.
        if (tunnelId > 0) {
            switch (encapsulationType) {
                case TRANSIT_VLAN:
                    matchVlan(ofFactory, builder, tunnelId);
                    break;
                case VXLAN:
                    matchVxlan(ofFactory, builder, tunnelId);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unknown encapsulation type: %s", encapsulationType));
            }
        }
    }

    private void matchVlan(final OFFactory ofFactory, final Match.Builder matchBuilder, final int vlanId) {
        if (0 <= OF_12.compareTo(ofFactory.getVersion())) {
            matchBuilder.setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId),
                    OFVlanVidMatch.ofRawVid(OF10_VLAN_MASK));
        } else {
            matchBuilder.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId));
        }
    }

    private void matchVxlan(OFFactory ofFactory, Match.Builder matchBuilder, long tunnelId) {
        if (OF_12.compareTo(ofFactory.getVersion()) >= 0) {
            throw new UnsupportedOperationException("Switch doesn't support tunnel_id match");
        } else {
            matchBuilder.setExact(MatchField.ETH_TYPE, EthType.IPv4);
            matchBuilder.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
            matchBuilder.setExact(MatchField.UDP_DST, TransportPort.of(VXLAN_UDP_DST));
            matchBuilder.setExact(MatchField.TUNNEL_ID, U64.of(tunnelId));
        }
    }

    /**
     * Pushes a single flow modification command to the switch with the given datapath ID.
     *
     * @param sw open flow switch descriptor
     * @param flowId flow name, for logging
     * @param flowMod command to send
     * @return OF transaction Id (???)
     * @throws OfInstallException openflow install exception
     */
    private long pushFlow(final IOFSwitch sw, final String flowId, final OFMessage flowMod) throws OfInstallException {
        logger.info("installing {} flow: {}", flowId, flowMod);

        if (!sw.write(flowMod)) {
            throw new OfInstallException(sw.getId(), flowMod);
        }

        return flowMod.getXid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IOFSwitch lookupSwitch(DatapathId dpId) throws SwitchNotFoundException {
        IOFSwitch sw = ofSwitchService.getActiveSwitch(dpId);
        if (sw == null) {
            throw new SwitchNotFoundException(dpId);
        }
        return sw;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InetAddress getSwitchIpAddress(IOFSwitch sw) {
        return ((InetSocketAddress) sw.getInetAddress()).getAddress();
    }

    @Override
    public List<OFPortDesc> getPhysicalPorts(DatapathId dpId) throws SwitchNotFoundException {
        return this.getPhysicalPorts(lookupSwitch(dpId));
    }

    @Override
    public List<OFPortDesc> getPhysicalPorts(IOFSwitch sw) {
        final Collection<OFPortDesc> ports = sw.getPorts();
        if (ports == null) {
            return ImmutableList.of();
        }

        return ports.stream()
                .filter(entry -> !OfPortDescConverter.INSTANCE.isReservedPort(entry.getPortNo()))
                .collect(toList());
    }

    // TODO(surabujin): this method can/should be moved to the RecordHandler level
    @Override
    public void configurePort(DatapathId dpId, int portNumber, Boolean portAdminDown) throws SwitchOperationException {
        IOFSwitch sw = lookupSwitch(dpId);

        boolean makeChanges = false;
        if (portAdminDown != null) {
            makeChanges = true;
            updatePortStatus(sw, portNumber, portAdminDown);
        }

        if (makeChanges) {
            sendBarrierRequest(sw);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OFPortDesc> dumpPortsDescription(DatapathId dpid) throws SwitchOperationException {
        IOFSwitch sw = lookupSwitch(dpid);

        return new ArrayList<>(sw.getPorts());
    }

    private void updatePortStatus(IOFSwitch sw, int portNumber, boolean isAdminDown) throws SwitchOperationException {
        Set<OFPortConfig> config = new HashSet<>(1);
        if (isAdminDown) {
            config.add(OFPortConfig.PORT_DOWN);
        }

        Set<OFPortConfig> portMask = ImmutableSet.of(OFPortConfig.PORT_DOWN);

        final OFFactory ofFactory = sw.getOFFactory();
        OFPortMod ofPortMod = ofFactory.buildPortMod()
                .setPortNo(OFPort.of(portNumber))
                // switch can argue against empty HWAddress (BAD_HW_ADDR) :(
                .setHwAddr(getPortHwAddress(sw, portNumber))
                .setConfig(config)
                .setMask(portMask)
                .build();

        if (!sw.write(ofPortMod)) {
            throw new SwitchOperationException(sw.getId(),
                    format("Unable to update port configuration: %s", ofPortMod));
        }

        logger.debug("Successfully updated port status {}", ofPortMod);
    }

    private MacAddress getPortHwAddress(IOFSwitch sw, int portNumber) throws SwitchOperationException {
        OFPortDesc portDesc = sw.getPort(OFPort.of(portNumber));
        if (portDesc == null) {
            throw new SwitchOperationException(sw.getId(),
                    format("Unable to get port by number %d on the switch %s",
                            portNumber, sw.getId()));
        }
        return portDesc.getHwAddr();
    }
}
