/* Copyright 2017 Telstra Open Source
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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openkilda.floodlight.pathverification.PathVerificationService.VERIFICATION_BCAST_PACKET_DST;
import static org.openkilda.messaging.Utils.ETH_TYPE;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_15;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.config.provider.ConfigurationProvider;
import org.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.openkilda.floodlight.switchmanager.web.SwitchManagerWebRoutable;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.payload.flow.OutputVlanType;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.PortChangeType;
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
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFLegacyMeterBandDrop;
import org.projectfloodlight.openflow.protocol.OFLegacyMeterFlags;
import org.projectfloodlight.openflow.protocol.OFLegacyMeterMod;
import org.projectfloodlight.openflow.protocol.OFLegacyMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFPortConfig;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortMod;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.Match.Builder;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
import java.util.stream.Stream;

/**
 * Created by jonv on 29/3/17.
 */
public class SwitchManager implements IFloodlightModule, IFloodlightService, ISwitchManager, IOFMessageListener {
    private static final Logger logger = LoggerFactory.getLogger(SwitchManager.class);

    /**
     * Make sure we clear the top bit .. that is for NON_SYSTEM_MASK. This mask is applied to
     * Cookie IDs when creating a flow.
     */
    public static final long FLOW_COOKIE_MASK = 0x7FFFFFFFFFFFFFFFL;

    static final U64 NON_SYSTEM_MASK = U64.of(0x80000000FFFFFFFFL);

    public static final int VERIFICATION_RULE_PRIORITY = FlowModUtils.PRIORITY_MAX - 1000;
    public static final int DEFAULT_RULE_PRIORITY = FlowModUtils.PRIORITY_HIGH;


    // This is invalid VID mask - it cut of highest bit that indicate presence of VLAN tag on package. But valid mask
    // 0x1FFF lead to rule reject during install attempt on accton based switches.
    private static short OF10_VLAN_MASK = 0x0FFF;

    private IFloodlightProviderService floodlightProvider;
    private IOFSwitchService ofSwitchService;
    private IRestApiService restApiService;
    private KafkaMessageProducer kafkaProducer;
    private ConnectModeRequest.Mode connectMode;

    private String topoDiscoTopic;

    // IFloodlightModule Methods

    /**
     * Create an OFInstructionApplyActions which applies actions.
     *
     * @param ofFactory OF factory for the switch
     * @param actionList OFAction list to apply
     * @return {@link OFInstructionApplyActions}
     */
    private static OFInstructionApplyActions buildInstructionApplyActions(OFFactory ofFactory,
            List<OFAction> actionList) {
        return ofFactory.instructions().applyActions(actionList).createBuilder().build();
    }

    /**
     * Returns legacy meter action.
     *
     * @param ofFactory OF factory for the switch
     * @param meterId meter id
     * @return {@link OFAction}
     */
    private static OFAction legacyMeterAction(final OFFactory ofFactory, final long meterId) {
        return ofFactory.actions().buildNiciraLegacyMeter().setMeterId(meterId).build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        services.add(ISwitchManager.class);
        return services;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<>();
        map.put(ISwitchManager.class, this);
        return map;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(3);
        services.add(IFloodlightProviderService.class);
        services.add(IOFSwitchService.class);
        services.add(IRestApiService.class);
        return services;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        ofSwitchService = context.getServiceImpl(IOFSwitchService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);

        ConfigurationProvider provider = ConfigurationProvider.of(context, this);
        KafkaTopicsConfig topicsConfig = provider.getConfiguration(KafkaTopicsConfig.class);
        topoDiscoTopic = topicsConfig.getTopoDiscoTopic();

        String connectModeProperty = provider.getConfiguration(SwitchManagerConfig.class).getConnectMode();
        try {
            connectMode = ConnectModeRequest.Mode.valueOf(connectModeProperty);
        } catch (Exception e) {
            logger.error("CONFIG EXCEPTION: connect-mode could not be set to {}, defaulting to AUTO",
                    connectModeProperty);
            connectMode = ConnectModeRequest.Mode.AUTO;
        }
        // TODO: Ensure Kafka Topics are created..
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting " + SwitchEventCollector.class.getCanonicalName());
        restApiService.addRestletRoutable(new SwitchManagerWebRoutable());
        floodlightProvider.addOFMessageListener(OFType.ERROR, this);
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
            kafkaProducer.postMessage("kilda.flow", error);
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
    public ConnectModeRequest.Mode connectMode(final ConnectModeRequest.Mode mode) {
        if (mode != null) {
            this.connectMode = mode;
        }
        return this.connectMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installDefaultRules(final DatapathId dpid) throws SwitchOperationException {
        installDropFlow(dpid);
        installVerificationRule(dpid, true);
        installVerificationRule(dpid, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long installIngressFlow(
            final DatapathId dpid, final String flowId,
            final Long cookie, final int inputPort, final int outputPort,
            final int inputVlanId, final int transitVlanId,
            final OutputVlanType outputVlanType, final long meterId) throws SwitchOperationException {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();


        // build meter instruction
        OFInstructionMeter meter = null;
        if (meterId != 0L && !OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            if (ofFactory.getVersion().compareTo(OF_12) <= 0) {
                actionList.add(legacyMeterAction(ofFactory, meterId));
            } else if (ofFactory.getVersion().compareTo(OF_15) == 0) {
                actionList.add(ofFactory.actions().buildMeter().setMeterId(meterId).build());
            } else /* OF_13, OF_14 */ {
                meter = ofFactory.instructions().buildMeter().setMeterId(meterId).build();
            }
        }

        // output action based on encap scheme
        actionList.addAll(inputVlanTypeToOfActionList(ofFactory, transitVlanId, outputVlanType));

        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(ofFactory, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(ofFactory, actionList);

        // build match by input port and input vlan id
        Match match = matchFlow(ofFactory, inputPort, inputVlanId);

        // build FLOW_MOD command with meter
        OFFlowMod flowMod = buildFlowMod(ofFactory, match, meter, actions,
                cookie & FLOW_COOKIE_MASK, DEFAULT_RULE_PRIORITY);

        return pushFlow(sw, "--InstallIngressFlow--", flowMod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long installEgressFlow(
            final DatapathId dpid, String flowId, final Long cookie,
            final int inputPort, final int outputPort,
            final int transitVlanId, final int outputVlanId,
            final OutputVlanType outputVlanType) throws SwitchOperationException {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();

        // build match by input port and transit vlan id
        Match match = matchFlow(ofFactory, inputPort, transitVlanId);

        // output action based on encap scheme
        actionList.addAll(outputVlanTypeToOfActionList(ofFactory, outputVlanId, outputVlanType));

        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(ofFactory, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(ofFactory, actionList);

        // build FLOW_MOD command, no meter
        OFFlowMod flowMod = buildFlowMod(ofFactory, match, null, actions,
                cookie & FLOW_COOKIE_MASK, DEFAULT_RULE_PRIORITY);

        return pushFlow(sw, "--InstallEgressFlow--", flowMod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long installTransitFlow(
            final DatapathId dpid, final String flowId,
            final Long cookie, final int inputPort, final int outputPort,
            final int transitVlanId) throws SwitchOperationException {
        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();

        // build match by input port and transit vlan id
        Match match = matchFlow(ofFactory, inputPort, transitVlanId);

        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(ofFactory, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(ofFactory, actionList);

        // build FLOW_MOD command, no meter
        OFFlowMod flowMod = buildFlowMod(ofFactory, match, null, actions,
                cookie & FLOW_COOKIE_MASK, DEFAULT_RULE_PRIORITY);

        return pushFlow(sw, flowId, flowMod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long installOneSwitchFlow(
            final DatapathId dpid, final String flowId,
            final Long cookie, final int inputPort,
            final int outputPort, final int inputVlanId,
            final int outputVlanId,
            final OutputVlanType outputVlanType, final long meterId) throws SwitchOperationException {
        // TODO: As per other locations, how different is this to IngressFlow? Why separate code path?
        //          As with any set of tests, the more we test the same code path, the better.
        //          Based on brief glance, this looks 90% the same as IngressFlow.

        List<OFAction> actionList = new ArrayList<>();
        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();


        // build meter instruction
        OFInstructionMeter meter = null;
        if (meterId != 0L && !OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            if (ofFactory.getVersion().compareTo(OF_12) <= 0) {
                actionList.add(legacyMeterAction(ofFactory, meterId));
            } else if (ofFactory.getVersion().compareTo(OF_15) == 0) {
                actionList.add(ofFactory.actions().buildMeter().setMeterId(meterId).build());
            } else /* OF_13, OF_14 */ {
                meter = ofFactory.instructions().buildMeter().setMeterId(meterId).build();
            }
        }

        // output action based on encap scheme
        actionList.addAll(pushSchemeOutputVlanTypeToOfActionList(ofFactory, outputVlanId, outputVlanType));
        // transmit packet from outgoing port
        actionList.add(actionSetOutputPort(ofFactory, outputPort));

        // build instruction with action list
        OFInstructionApplyActions actions = buildInstructionApplyActions(ofFactory, actionList);

        // build match by input port and transit vlan id
        Match match = matchFlow(ofFactory, inputPort, inputVlanId);

        // build FLOW_MOD command with meter
        OFFlowMod flowMod = buildFlowMod(ofFactory, match, meter, actions,
                cookie & FLOW_COOKIE_MASK, DEFAULT_RULE_PRIORITY);

        pushFlow(sw, flowId, flowMod);

        return flowMod.getXid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OFFlowStatsEntry> dumpFlowTable(final DatapathId dpid) {
        List<OFFlowStatsEntry> entries = new ArrayList<>();
        IOFSwitch sw = ofSwitchService.getSwitch(dpid);
        if (sw == null) {
            throw new IllegalArgumentException(String.format("Switch %s was not found", dpid));
        }

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
                        .collect(Collectors.toList());
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            logger.error("Could not get flow stats for {}.", dpid, e);
        }

        return entries;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OFMeterConfigStatsReply dumpMeters(final DatapathId dpid) throws SwitchOperationException {
        OFMeterConfigStatsReply values = null;
        IOFSwitch sw = lookupSwitch(dpid);
        if (sw == null) {
            throw new IllegalArgumentException(String.format("Switch %s was not found", dpid));
        }

        OFFactory ofFactory = sw.getOFFactory();
        if (ofFactory.getVersion().compareTo(OF_13) < 0) {
            throw new UnsupportedSwitchOperationException(dpid,
                    String.format("Dumping of meters is not supported on the requested switch %s.", dpid));
        }

        OFMeterConfigStatsRequest meterRequest = ofFactory.buildMeterConfigStatsRequest()
                .setMeterId(0xffffffff)
                .build();

        try {
            ListenableFuture<OFMeterConfigStatsReply> future = sw.writeRequest(meterRequest);
            values = future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            logger.error("Could not get meter config stats for {}.", dpid, e);
        }

        return values;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long installMeter(final DatapathId dpid, final long bandwidth, final long burstSize, final long meterId)
            throws SwitchOperationException {
        if (meterId == 0) {
            logger.info("skip installing meter {} on switch {} width bandwidth {}", meterId, dpid, bandwidth);
            return 0L;
        }

        IOFSwitch sw = lookupSwitch(dpid);

        if (OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            logger.info("skip installing meter {} on OVS switch {} width bandwidth {}", meterId, dpid, bandwidth);
            return 0L;
        }

        long meterCommandXid;

        if (sw.getOFFactory().getVersion().compareTo(OF_12) <= 0) {
            meterCommandXid = installLegacyMeter(sw, dpid, bandwidth, burstSize, meterId);
        } else {
            meterCommandXid = buildAndinstallMeter(sw, dpid, bandwidth, burstSize, meterId);
        }

        // All cases when we're installing meters require that we wait until the command is processed and the meter is
        // installed.
        sendBarrierRequest(sw);

        return meterCommandXid;
    }

    @Override
    public Map<DatapathId, IOFSwitch> getAllSwitchMap() {
        return ofSwitchService.getAllSwitchMap();
    }

    // Utility Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public long deleteMeter(final DatapathId dpid, final long meterId)
            throws SwitchOperationException {
        if (meterId == 0) {
            logger.info("skip deleting meter {} from switch {}", meterId, dpid);
            return 0L;
        }

        IOFSwitch sw = lookupSwitch(dpid);
        if (OVS_MANUFACTURER.equals(sw.getSwitchDescription().getManufacturerDescription())) {
            logger.info("skip deleting meter {} from OVS switch {}", meterId, dpid);
            return 0L;
        }

        if (sw.getOFFactory().getVersion().compareTo(OF_12) <= 0) {
            return deleteLegacyMeter(sw, dpid, meterId);
        } else {
            return buildAndDeleteMeter(sw, dpid, meterId);
        }
    }



    @Override
    public List<Long> deleteAllNonDefaultRules(final DatapathId dpid) throws SwitchOperationException {
        List<OFFlowStatsEntry> flowStatsBefore = dumpFlowTable(dpid);
        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();

        Set<Long> removedRules = new HashSet<>();

        for (OFFlowStatsEntry flowStatsEntry : flowStatsBefore) {
            long flowCookie = flowStatsEntry.getCookie().getValue();
            if (flowCookie != DROP_RULE_COOKIE
                    && flowCookie != VERIFICATION_BROADCAST_RULE_COOKIE
                    && flowCookie != VERIFICATION_UNICAST_RULE_COOKIE) {
                OFFlowDelete flowDelete = ofFactory.buildFlowDelete()
                        .setCookie(U64.of(flowCookie))
                        .setCookieMask(U64.NO_MASK)
                        .build();
                pushFlow(sw, "--DeleteFlow--", flowDelete);

                logger.info("Rule with cookie {} is to be removed from switch {}.", flowCookie, dpid);

                removedRules.add(flowCookie);
            }
        }

        // Wait for OFFlowDelete to be processed.
        sendBarrierRequest(sw);

        List<OFFlowStatsEntry> flowStatsAfter = dumpFlowTable(dpid);
        Set<Long> cookiesAfter = flowStatsAfter.stream()
                .map(entry -> entry.getCookie().getValue())
                .collect(Collectors.toSet());

        flowStatsBefore.stream()
                .map(entry -> entry.getCookie().getValue())
                .filter(cookie -> !cookiesAfter.contains(cookie))
                .filter(cookie -> !removedRules.contains(cookie))
                .forEach(cookie -> {
                    logger.warn("Rule with cookie {} has been removed although not requested. Switch {}.", cookie,
                            dpid);
                    removedRules.add(cookie);
                });

        cookiesAfter.stream()
                .filter(removedRules::contains)
                .forEach(cookie -> {
                    logger.warn("Rule with cookie {} was requested to be removed, but it still remains. Switch {}.",
                            cookie, dpid);
                    removedRules.remove(cookie);
                });

        return new ArrayList<>(removedRules);
    }


    @Override
    public List<Long> deleteRulesByCriteria(final DatapathId dpid, DeleteRulesCriteria... criteria)
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
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> deleteDefaultRules(final DatapathId dpid) throws SwitchOperationException {
        return deleteRulesWithCookie(dpid, DROP_RULE_COOKIE, VERIFICATION_BROADCAST_RULE_COOKIE,
                VERIFICATION_UNICAST_RULE_COOKIE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void installVerificationRule(final DatapathId dpid, final boolean isBroadcast)
            throws SwitchOperationException {
        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();

        // Don't install the unicast for OpenFlow 1.2 doesn't work properly
        if (!isBroadcast) {
            if (ofFactory.getVersion().compareTo(OF_12) > 0) {
                logger.debug("installing unicast verification match for {}", dpid);
            } else {
                logger.debug("not installing unicast verification match for {}", dpid);
                return;
            }
        }

        logger.debug("installing verification rule for {}", dpid);

        Match match = matchVerification(sw, isBroadcast);
        ArrayList<OFAction> actionList = new ArrayList<>(2);
        actionList.add(actionSendToController(sw));
        actionList.add(actionSetDstMac(sw, dpidToMac(sw)));
        OFInstructionApplyActions instructionApplyActions = ofFactory.instructions()
                .applyActions(actionList).createBuilder().build();
        final long cookie = isBroadcast ? VERIFICATION_BROADCAST_RULE_COOKIE : VERIFICATION_UNICAST_RULE_COOKIE;
        OFFlowMod flowMod = buildFlowMod(ofFactory, match, null, instructionApplyActions,
                cookie, VERIFICATION_RULE_PRIORITY);
        String flowname = (isBroadcast) ? "Broadcast" : "Unicast";
        flowname += "--VerificationFlow--" + dpid.toString();
        pushFlow(sw, flowname, flowMod);
    }

    /**
     * Installs custom drop rule .. ie cookie, priority, match
     *
     * @param dpid datapathId of switch
     * @param dstMac Destination Mac address to match on
     * @param dstMask Destination Mask to match on
     * @param cookie Cookie to use for this rule
     * @param priority Priority of the rule
     * @throws SwitchOperationException switch operation exception
     */
    @Override
    public void installDropFlowCustom(final DatapathId dpid, String dstMac, String dstMask,
                                      final long cookie, final int priority) throws SwitchOperationException {
        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();

        Match match = simpleDstMatch(ofFactory, dstMac, dstMask);
        OFFlowMod flowMod = buildFlowMod(ofFactory, match, null, null, cookie, priority);
        String flowName = "--CustomDropRule--" + dpid.toString();
        pushFlow(sw, flowName, flowMod);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void installDropFlow(final DatapathId dpid) throws SwitchOperationException {
        // TODO: leverage installDropFlowCustom
        IOFSwitch sw = lookupSwitch(dpid);
        OFFactory ofFactory = sw.getOFFactory();

        if (ofFactory.getVersion() == OF_12) {
            logger.debug("Skip installation of drop flow for switch {}", dpid);
        } else {
            logger.debug("Installing drop flow for switch {}", dpid);
            OFFlowMod flowMod = buildFlowMod(ofFactory, null, null, null, DROP_RULE_COOKIE, 1);
            String flowName = "--DropRule--" + dpid.toString();
            pushFlow(sw, flowName, flowMod);
        }
    }

    private long buildAndinstallMeter(final IOFSwitch sw, final DatapathId dpid, final long bandwidth,
                                      final long burstSize, final long meterId)
            throws OFInstallException {
        logger.debug("installing meter {} on switch {} width bandwidth {}", meterId, dpid, bandwidth);

        Set<OFMeterFlags> flags = new HashSet<>(asList(OFMeterFlags.KBPS, OFMeterFlags.BURST));
        OFFactory ofFactory = sw.getOFFactory();

        OFMeterBandDrop.Builder bandBuilder = ofFactory.meterBands()
                .buildDrop()
                .setRate(bandwidth)
                .setBurstSize(burstSize);

        OFMeterMod.Builder meterModBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterId)
                .setCommand(OFMeterModCommand.ADD)
                .setFlags(flags);

        if (sw.getOFFactory().getVersion().compareTo(OF_13) > 0) {
            meterModBuilder.setBands(singletonList(bandBuilder.build()));
        } else {
            meterModBuilder.setMeters(singletonList(bandBuilder.build()));
        }

        OFMeterMod meterMod = meterModBuilder.build();

        return pushFlow(sw, "--InstallMeter--", meterMod);
    }

    private long installLegacyMeter(
            final IOFSwitch sw, final DatapathId dpid,
            final long bandwidth, final long burstSize, final long meterId)
            throws OFInstallException {
        logger.debug("installing legacy meter {} on OVS switch {} width bandwidth {}", meterId, dpid, bandwidth);

        Set<OFLegacyMeterFlags> flags = new HashSet<>(asList(OFLegacyMeterFlags.KBPS, OFLegacyMeterFlags.BURST));
        OFFactory ofFactory = sw.getOFFactory();

        OFLegacyMeterBandDrop.Builder bandBuilder = ofFactory.legacyMeterBandDrop(bandwidth, burstSize).createBuilder();

        OFLegacyMeterMod meterMod = ofFactory.buildLegacyMeterMod()
                .setMeterId(meterId)
                .setCommand(OFLegacyMeterModCommand.ADD)
                .setMeters(singletonList(bandBuilder.build()))
                .setFlags(flags)
                .build();

        return pushFlow(sw, "--InstallMeter", meterMod);
    }

    private long buildAndDeleteMeter(IOFSwitch sw, final DatapathId dpid, final long meterId)
            throws OFInstallException {
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

        return pushFlow(sw, "--DeleteMeter--", meterDelete);
    }

    private long deleteLegacyMeter(final IOFSwitch sw, final DatapathId dpid, final long meterId)
            throws OFInstallException {
        logger.debug("deleting legacy meter {} from switch {}", meterId, dpid);

        OFFactory ofFactory = sw.getOFFactory();

        OFLegacyMeterMod meterDelete = ofFactory.buildLegacyMeterMod()
                .setMeterId(meterId)
                .setMeters(emptyList())
                .setCommand(OFLegacyMeterModCommand.DELETE)
                .build();

        return pushFlow(sw, "--DeleteMeter--", meterDelete);
    }

    private OFFlowDelete buildFlowDeleteByCriteria(OFFactory ofFactory, DeleteRulesCriteria criteria) {
        OFFlowDelete.Builder builder = ofFactory.buildFlowDelete();
        if (criteria.getCookie() != null) {
            builder.setCookie(U64.of(criteria.getCookie()));
            builder.setCookieMask(U64.NO_MASK);
        }

        if (criteria.getInPort() != null) {
            // Match either In Port or both Port & Vlan criteria.
            Match match = matchFlow(ofFactory, criteria.getInPort(),
                    Optional.ofNullable(criteria.getInVlan()).orElse(0));
            builder.setMatch(match);

        } else if (criteria.getInVlan() != null) {
            // Match In Vlan criterion if In Port is not specified
            Match.Builder matchBuilder = ofFactory.buildMatch();
            matchVlan(ofFactory, matchBuilder, criteria.getInVlan());
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

        return builder.build();
    }

    private OFBarrierReply sendBarrierRequest(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();
        OFBarrierRequest barrierRequest = ofFactory.buildBarrierRequest().build();

        OFBarrierReply result = null;
        try {
            ListenableFuture<OFBarrierReply> future = sw.writeRequest(barrierRequest);
            result = future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            logger.error("Could not get a barrier reply for {}.", sw.getId(), e);
        }
        return result;
    }


    private List<Long> deleteRulesWithCookie(final DatapathId dpid, Long... cookiesToRemove)
            throws SwitchOperationException {
        DeleteRulesCriteria[] criteria = Stream.of(cookiesToRemove)
                .map(cookie -> DeleteRulesCriteria.builder().cookie(cookie).build())
                .toArray(DeleteRulesCriteria[]::new);

        return deleteRulesByCriteria(dpid, criteria);
    }

    /**
     * Creates a Match based on an inputPort and VlanID.
     * NB1: that this match only matches on the outer most tag which must be of ether-type 0x8100.
     * NB2: vlanId of 0 means match on port, not vlan
     *
     * @param ofFactory OF factory for the switch
     * @param inputPort input port for the match
     * @param vlanId    vlanID to match on; 0 means match on port
     * @return {@link Match}
     */
    private Match matchFlow(final OFFactory ofFactory, final int inputPort, final int vlanId) {
        Match.Builder mb = ofFactory.buildMatch();
        //
        // Extra emphasis: vlan of 0 means match on port on not VLAN.
        //
        mb.setExact(MatchField.IN_PORT, OFPort.of(inputPort));
        if (vlanId > 0) {
            matchVlan(ofFactory, mb, vlanId);
        }

        return mb.build();
    }

    private void matchVlan(final OFFactory ofFactory, final Match.Builder matchBuilder, final int vlanId) {
        if (0 <= OF_12.compareTo(ofFactory.getVersion())) {
            matchBuilder.setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId),
                    OFVlanVidMatch.ofRawVid(OF10_VLAN_MASK));
        } else {
            matchBuilder.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId));
        }
    }

    /**
     * Builds OFAction list based on flow parameters for replace scheme.
     *
     * @param ofFactory OF factory for the switch
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return list of {@link OFAction}
     */
    private List<OFAction> replaceSchemeOutputVlanTypeToOfActionList(OFFactory ofFactory, int outputVlanId,
                                                                     OutputVlanType outputVlanType) {
        List<OFAction> actionList;

        switch (outputVlanType) {
            case PUSH:
            case REPLACE:
                actionList = singletonList(actionReplaceVlan(ofFactory, outputVlanId));
                break;
            case POP:
            case NONE:
                actionList = singletonList(actionPopVlan(ofFactory));
                break;
            default:
                actionList = emptyList();
                logger.error("Unknown OutputVlanType: " + outputVlanType);
        }

        return actionList;
    }

    /**
     * Builds OFAction list based on flow parameters for push scheme.
     *
     * @param ofFactory OF factory for the switch
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return list of {@link OFAction}
     */
    private List<OFAction> pushSchemeOutputVlanTypeToOfActionList(OFFactory ofFactory, int outputVlanId,
                                                                  OutputVlanType outputVlanType) {
        List<OFAction> actionList = new ArrayList<>(2);

        switch (outputVlanType) {
            case PUSH:      // No VLAN on packet so push a new one
                actionList.add(actionPushVlan(ofFactory, ETH_TYPE));
                actionList.add(actionReplaceVlan(ofFactory, outputVlanId));
                break;
            case REPLACE:   // VLAN on packet but needs to be replaced
                actionList.add(actionReplaceVlan(ofFactory, outputVlanId));
                break;
            case POP:       // VLAN on packet, so remove it
                // TODO:  can i do this?  pop two vlan's back to back...
                actionList.add(actionPopVlan(ofFactory));
                break;
            case NONE:
                break;
            default:
                logger.error("Unknown OutputVlanType: " + outputVlanType);
        }

        return actionList;
    }

    /**
     * Chooses encapsulation scheme for building OFAction list.
     *
     * @param ofFactory OF factory for the switch
     * @param outputVlanId   set vlan on packet before forwarding via outputPort; 0 means not to set
     * @param outputVlanType type of action to apply to the outputVlanId if greater than 0
     * @return list of {@link OFAction}
     */
    private List<OFAction> outputVlanTypeToOfActionList(OFFactory ofFactory, int outputVlanId,
                                                        OutputVlanType outputVlanType) {
        return replaceSchemeOutputVlanTypeToOfActionList(ofFactory, outputVlanId, outputVlanType);
    }

    /**
     * Chooses encapsulation scheme for building OFAction list.
     *
     * @param ofFactory OF factory for the switch
     * @param transitVlanId set vlan on packet or replace it before forwarding via outputPort; 0 means not to set
     * @return list of {@link OFAction}
     */
    private List<OFAction> inputVlanTypeToOfActionList(OFFactory ofFactory, int transitVlanId,
                                                       OutputVlanType outputVlanType) {
        List<OFAction> actionList = new ArrayList<>(3);
        if (OutputVlanType.PUSH.equals(outputVlanType) || OutputVlanType.NONE.equals(outputVlanType)) {
            actionList.add(actionPushVlan(ofFactory, ETH_TYPE));
        }
        actionList.add(actionReplaceVlan(ofFactory, transitVlanId));
        return actionList;
    }

    /**
     * Create an OFAction which sets the output port.
     *
     * @param ofFactory OF factory for the switch
     * @param outputPort port to set in the action
     * @return {@link OFAction}
     */
    private OFAction actionSetOutputPort(final OFFactory ofFactory, final int outputPort) {
        OFActions actions = ofFactory.actions();
        return actions.buildOutput().setMaxLen(0xFFFFFFFF).setPort(OFPort.of(outputPort)).build();
    }

    /**
     * Create an OFAction to change the outer most vlan.
     *
     * @param factory OF factory for the switch
     * @param newVlan final VLAN to be set on the packet
     * @return {@link OFAction}
     */
    private OFAction actionReplaceVlan(final OFFactory factory, final int newVlan) {
        OFOxms oxms = factory.oxms();
        OFActions actions = factory.actions();

        if (OF_12.compareTo(factory.getVersion()) == 0) {
            return actions.buildSetField().setField(oxms.buildVlanVid()
                    .setValue(OFVlanVidMatch.ofRawVid((short) newVlan))
                    .build()).build();
        } else {
            return actions.buildSetField().setField(oxms.buildVlanVid()
                    .setValue(OFVlanVidMatch.ofVlan(newVlan))
                    .build()).build();
        }
    }

    /**
     * Create an OFAction to add a VLAN header.
     *
     * @param ofFactory OF factory for the switch
     * @param etherType ethernet type of the new VLAN header
     * @return {@link OFAction}
     */
    private OFAction actionPushVlan(final OFFactory ofFactory, final int etherType) {
        OFActions actions = ofFactory.actions();
        return actions.buildPushVlan().setEthertype(EthType.of(etherType)).build();
    }

    /**
     * Create an OFAction to remove the outer most VLAN.
     *
     * @param ofFactory OF factory for the switch
     * @return {@link OFAction}
     */
    private OFAction actionPopVlan(final OFFactory ofFactory) {
        OFActions actions = ofFactory.actions();
        return actions.popVlan();
    }

    /**
     * Create an OFFlowMod that can be passed to StaticEntryPusher.
     *
     * @param ofFactory OF factory for the switch
     * @param match    match for the flow
     * @param meter    meter for the flow
     * @param actions  actions for the flow
     * @param cookie   cookie for the flow
     * @param priority priority to set on the flow
     * @return {@link OFFlowMod}
     */
    private OFFlowMod buildFlowMod(final OFFactory ofFactory, final Match match, final OFInstructionMeter meter,
                                   final OFInstructionApplyActions actions, final long cookie, final int priority) {
        OFFlowMod.Builder fmb = ofFactory.buildFlowAdd();
        fmb.setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setHardTimeout(FlowModUtils.INFINITE_TIMEOUT);
        fmb.setBufferId(OFBufferId.NO_BUFFER);
        fmb.setCookie(U64.of(cookie));
        fmb.setPriority(priority);
        List<OFInstruction> instructions = new ArrayList<>(2);

        // If no meter then no bandwidth limit
        if (meter != null) {
            instructions.add(meter);
        }

        // If no instruction then Drops packet
        if (actions != null) {
            instructions.add(actions);
        }

        // If no then match everything
        if (match != null) {
            fmb.setMatch(match);
        }

        return fmb.setInstructions(instructions).build();
    }

    /**
     * Create a MAC address based on the DPID.
     *
     * @param sw switch object
     * @return {@link MacAddress}
     */
    private MacAddress dpidToMac(final IOFSwitch sw) {
        return MacAddress.of(Arrays.copyOfRange(sw.getId().getBytes(), 2, 8));
    }

    /**
     * Create a match object for the verification packets.
     *
     * @param sw          siwtch object
     * @param isBroadcast if broadcast then set a generic match; else specific to switch Id
     * @return {@link Match}
     */
    private Match matchVerification(final IOFSwitch sw, final boolean isBroadcast) {
        MacAddress dstMac = isBroadcast ? MacAddress.of(VERIFICATION_BCAST_PACKET_DST) : dpidToMac(sw);
        Builder builder = sw.getOFFactory().buildMatch();
        builder.setMasked(MatchField.ETH_DST, dstMac, MacAddress.NO_MASK);
        return builder.build();
    }

  
  
    /**
     * Create an action to send packet to the controller.
     *
     * @param sw switch object
     * @return {@link OFAction}
     */
    private OFAction actionSendToController(final IOFSwitch sw) {
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildOutput().setMaxLen(0xFFffFFff).setPort(OFPort.CONTROLLER)
                .build();
    }

    /**
     * Create an action to set the DstMac of a packet.
     *
     * @param sw         switch object
     * @param macAddress MacAddress to set
     * @return {@link OFAction}
     */
    private OFAction actionSetDstMac(final IOFSwitch sw, final MacAddress macAddress) {
        OFOxms oxms = sw.getOFFactory().oxms();
        OFActions actions = sw.getOFFactory().actions();
        return actions.buildSetField()
                .setField(oxms.buildEthDst().setValue(macAddress).build()).build();
    }



    /**
     * A simple Match rule based on destination mac address and mask.
     * TODO: Could be generalized
     *
     * @param ofFactory OF factory for the switch
     * @param dstMac Destination Mac address to match on
     * @param dstMask Destination Mask to match on
     * @return Match
     */
    private Match simpleDstMatch(OFFactory ofFactory, String dstMac, String dstMask) {
        Match match = null;
        if (dstMac != null && dstMask != null && dstMac.length() > 0 && dstMask.length() > 0) {
            Builder builder = ofFactory.buildMatch();
            builder.setMasked(MatchField.ETH_DST, MacAddress.of(dstMac), MacAddress.NO_MASK);
            match = builder.build();
        }
        return match;
    }



    /**
     * Pushes a single flow modification command to the switch with the given datapath ID.
     *
     * @param sw      open flow switch descriptor
     * @param flowId  flow name, for logging
     * @param flowMod command to send
     * @return OF transaction Id (???)
     * @throws OFInstallException openflow install exception
     */
    private long pushFlow(final IOFSwitch sw, final String flowId, final OFMessage flowMod) throws OFInstallException {
        logger.info("installing {} flow: {}", flowId, flowMod);

        if (! sw.write(flowMod)) {
            throw new OFInstallException(sw.getId(), flowMod);
        }

        return flowMod.getXid();
    }

    /**
     * Wrap IOFSwitchService.getSwitch call to check protect from null return value.
     *
     * @param  dpId switch identifier
     * @return open flow switch descriptor
     * @throws SwitchOperationException switch operation exception
     */
    private IOFSwitch lookupSwitch(DatapathId dpId) throws SwitchOperationException {
        IOFSwitch swInfo = ofSwitchService.getSwitch(dpId);
        if (swInfo == null) {
            throw new SwitchOperationException(dpId, String.format("Switch %s was not found", dpId));
        }
        return swInfo;
    }


    /**
     * A struct to collect all the data necessary to manage the safe application of base rules.
     */
    private static final class SafeData {
        // Any switch rule with a priority less than this will be ignored
        static final int PRIORITY_IGNORE_THRESHOLD = 100;
        private static final int window = 5;
        // Used to filter out rules with low packet counts .. only test rules with more packets than this
        private static final int PACKET_COUNT_MIN = 5;

        DatapathId dpid;

        /**
         * The time of data collections may be inconsistent .. so if we try to see whether the rate
         * of data is different .. then use the captured timestamps to get an average.
         */
        List<Long> timestamps;
        Map<Long, List<Long>> ruleByteCounts; // counter per cookie per timestamp
        Map<Long, List<Long>> rulePktCounts;  // counter per cookie per timestamp
        // Stages - 0 = not started; 1 = applied; 2 = okay; 3 = removed (too many errors)
        int dropRuleStage;
        int broadcastRuleStage;
        int unicastRuleStage;

        void consumeData(long timestamp, List<OFFlowStatsEntry> flowEntries) {
            timestamps.add(timestamp);

            for (OFFlowStatsEntry flowStatsEntry : flowEntries) {
                if (flowStatsEntry.getPriority() <= PRIORITY_IGNORE_THRESHOLD) {
                    continue;
                }

                long flowCookie = flowStatsEntry.getCookie().getValue();
                if (!ruleByteCounts.containsKey(flowCookie)) {
                    ruleByteCounts.put(flowCookie, new ArrayList<>());
                    rulePktCounts.put(flowCookie, new ArrayList<>());
                }
                ruleByteCounts.get(flowCookie).add(flowStatsEntry.getByteCount().getValue());
                rulePktCounts.get(flowCookie).add(flowStatsEntry.getPacketCount().getValue());
            }
        }

        // collect 2 windows per stage .. apply rule after first window
        boolean shouldApplyRule(int stage) {
            return timestamps.size() == ((stage - 1) * 2 + 1) * window;
        }

        boolean shouldTestRule(int stage) {
            return timestamps.size() == ((stage - 1) * 2 + 2) * window;
        }

        // Starting with just the effect on packet count
        List<Integer> getRuleEffect(int stage) {
            int start = (stage - 1) * 2;
            int middle = start + 1;
            int end = middle + 1;
            int goodCounts = 0;
            int badCounts = 0;

            for (List<Long> packets : rulePktCounts.values()) {
                long packetsBefore = packets.get(middle) - packets.get(start);
                // We shouldn't start at the middle .. since we wouldn't have applied the rule yet.
                // So, start at middle+1 .. that is the first data point after applying the rule.
                long packetsAfter = packets.get(end) - packets.get(middle + 1);
                boolean ruleHadNoEffect = (packetsBefore > PACKET_COUNT_MIN && packetsAfter > 0);
                if (ruleHadNoEffect) {
                    goodCounts++;
                } else {
                    badCounts++;
                }
            }
            return asList(badCounts, goodCounts);
        }

        boolean isRuleOkay(List<Integer> ruleEffect) {
            // Initial algorithm: if any rule was sending data and then stopped, then applied rule
            // is not okay.
            // The first array element has the count of "bad_counts" .. ie packet count before rule
            // wasn't zero, but was zero after.
            int badCounts = ruleEffect.get(0);
            return badCounts == 0;
        }
    }

    private Map<DatapathId, SafeData> safeSwitches = new HashMap<>();
    private long lastRun = 0L;

    /**
     * {@inheritDoc}
     */
    @Override
    public void startSafeMode(final DatapathId dpid) {
        // Don't create a new object if one already exists .. ie, don't restart the process of
        // installing base rules.
        if (!safeSwitches.containsKey(dpid)) {
            SafeData safeData = safeSwitches.put(dpid, new SafeData());
            safeData.dpid = dpid;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopSafeMode(final DatapathId dpid) {
        safeSwitches.remove(dpid);
    }

    private static final long tick_length = 1000;
    private static final boolean BROADCAST = true;
    private static final int DROP_STAGE = 1;
    private static final int BROADCAST_STAGE = 2;
    private static final int UNICAST_STAGE = 3;
    // NB: The logic in safeModeTick relies on these RULE_* numbers. Mostly, it relies on the
    // IS_GOOD and NO_GOOD being greater that TESTED. And in reality, TESTED is just the lower
    // of IS_GOOD and NO_GOOD.
    private static final int RULE_APPLIED = 1;
    private static final int RULE_TESTED = 2;
    private static final int RULE_IS_GOOD = 2;
    private static final int RULE_NO_GOOD = 3;

    @Override
    public void safeModeTick() {
        // this may be called sporadically, so we'll need to measure the time between calls ..
        long time = System.currentTimeMillis();
        if (time - lastRun < tick_length) {
            return;
        }

        lastRun = time;

        Collection<SafeData> values = safeSwitches.values();
        for (SafeData safeData : values) {
            // Grab switch rule stats .. X pre and post .. X for 0, X for 1 .. make a decision.
            try {
                safeData.consumeData(time, dumpFlowTable(safeData.dpid));
                int datapoints = safeData.timestamps.size();

                if (safeData.dropRuleStage < RULE_TESTED) {

                    logger.debug("SAFE MODE: Collected Data during Drop Rule Stage for '{}' ", safeData.dpid);
                    if (safeData.shouldApplyRule(DROP_STAGE)) {
                        logger.info("SAFE MODE: APPLY Drop Rule for '{}' ", safeData.dpid);
                        safeData.dropRuleStage = RULE_APPLIED;
                        installDropFlow(safeData.dpid);
                    } else if (safeData.shouldTestRule(DROP_STAGE)) {
                        List<Integer> ruleEffect = safeData.getRuleEffect(DROP_STAGE);
                        if (safeData.isRuleOkay(ruleEffect)) {
                            logger.info("SAFE MODE: Drop Rule is GOOD for '{}' ", safeData.dpid);
                            safeData.dropRuleStage = RULE_IS_GOOD;
                        } else {
                            logger.warn("SAFE MODE: Drop Rule is BAD for '{}'. "
                                            + "Good Packet Count: {}. Bad Packet Count: {} ",
                                    safeData.dpid, ruleEffect.get(0), ruleEffect.get(1));
                            safeData.dropRuleStage = RULE_NO_GOOD;
                            deleteRulesWithCookie(safeData.dpid, ISwitchManager.DROP_RULE_COOKIE);
                        }
                    }

                } else if (safeData.broadcastRuleStage < RULE_TESTED) {

                    logger.debug("SAFE MODE: Collected Data during Broadcast Verification Rule "
                            + "Stage for '{}' ", safeData.dpid);
                    if (safeData.shouldApplyRule(BROADCAST_STAGE)) {
                        logger.info("SAFE MODE: APPLY Broadcast Verification Rule for '{}' ", safeData.dpid);
                        safeData.broadcastRuleStage = RULE_APPLIED;
                        installVerificationRule(safeData.dpid, BROADCAST);
                    } else if (safeData.shouldTestRule(BROADCAST_STAGE)) {
                        List<Integer> ruleEffect = safeData.getRuleEffect(BROADCAST_STAGE);
                        if (safeData.isRuleOkay(ruleEffect)) {
                            logger.info("SAFE MODE: Broadcast Verification Rule is GOOD for '{}' ", safeData.dpid);
                            safeData.broadcastRuleStage = RULE_IS_GOOD;
                        } else {
                            logger.warn("SAFE MODE: Broadcast Verification Rule is BAD for '{}'. "
                                            + "Good Packet Count: {}. Bad Packet Count: {} ",
                                    safeData.dpid, ruleEffect.get(0), ruleEffect.get(1));
                            safeData.broadcastRuleStage = RULE_NO_GOOD;
                            deleteRulesWithCookie(safeData.dpid, ISwitchManager.VERIFICATION_BROADCAST_RULE_COOKIE);
                        }
                    }
                } else if (safeData.unicastRuleStage < RULE_TESTED) {

                    // TODO: make this smarter and advance the unicast if unicast not applied.
                    logger.debug("SAFE MODE: Collected Data during Unicast Verification Rule Stage "
                            + "for '{}' ", safeData.dpid);
                    if (safeData.shouldApplyRule(UNICAST_STAGE)) {
                        logger.info("SAFE MODE: APPLY Unicast Verification Rule for '{}' ", safeData.dpid);
                        safeData.unicastRuleStage = RULE_APPLIED;
                        installVerificationRule(safeData.dpid, !BROADCAST);
                    } else if (safeData.shouldTestRule(UNICAST_STAGE)) {
                        List<Integer> ruleEffect = safeData.getRuleEffect(UNICAST_STAGE);
                        if (safeData.isRuleOkay(ruleEffect)) {
                            logger.info("SAFE MODE: Unicast Verification Rule is GOOD for '{}' ", safeData.dpid);
                            safeData.unicastRuleStage = RULE_IS_GOOD;
                        } else {
                            logger.warn("SAFE MODE: Unicast Verification Rule is BAD for '{}'. "
                                            + "Good Packet Count: {}. Bad Packet Count: {} ",
                                    safeData.dpid, ruleEffect.get(0), ruleEffect.get(1));
                            safeData.unicastRuleStage = RULE_NO_GOOD;
                            deleteRulesWithCookie(safeData.dpid, ISwitchManager.VERIFICATION_UNICAST_RULE_COOKIE);
                        }
                    }

                } else {
                    // once done with installing rules, we need to notify kilda that the switch is up
                    // and that ports up.
                    logger.info("SAFE MODE: COMPLETED base rules for '{}' ", safeData.dpid);
                    IOFSwitch sw = lookupSwitch(safeData.dpid);
                    sendSwitchActivate(sw);
                    sendPortUpEvents(sw);
                    // WE ARE DONE!! Remove ourselves from the list.
                    values.remove(safeData);  // will be reflected in safeSwitches
                }
            } catch (SwitchOperationException e) {
                logger.error("Error while switch {} was in safe mode. Removing switch from safe "
                        + "mode and NOT SENDING ACTIVATION. \nERROR: {}", safeData.dpid, e);
                values.remove(safeData);
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void sendSwitchActivate(final IOFSwitch sw) throws SwitchOperationException {
        Message message = SwitchEventCollector.buildSwitchMessage(sw, SwitchState.ACTIVATED);
        kafkaProducer.postMessage(topoDiscoTopic, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendPortUpEvents(final IOFSwitch sw) throws SwitchOperationException {
        if (sw.getEnabledPortNumbers() != null) {
            for (OFPort p : sw.getEnabledPortNumbers()) {
                if (SwitchEventCollector.isPhysicalPort(p)) {
                    kafkaProducer.postMessage(topoDiscoTopic,
                            SwitchEventCollector.buildPortMessage(sw.getId(), p,
                                    PortChangeType.UP));
                }
            }
        }
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
                    String.format("Unable to update port configuration: %s", ofPortMod));
        }

        logger.debug("Successfully updated port status {}", ofPortMod);
    }

    private MacAddress getPortHwAddress(IOFSwitch sw, int portNumber) {
        OFPortDesc portDesc = sw.getPort(OFPort.of(portNumber));
        return portDesc.getHwAddr();
    }
}
