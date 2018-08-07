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

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.config.provider.ConfigurationProvider;
import org.openkilda.floodlight.converter.IOFSwitchConverter;
import org.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.switches.ConnectModeRequest;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SwitchEventCollector implements IFloodlightModule, IOFSwitchListener, IFloodlightService {
    private static final Logger logger = LoggerFactory.getLogger(SwitchEventCollector.class);

    private IOFSwitchService switchService;
    private KafkaMessageProducer kafkaProducer;
    private ISwitchManager switchManager;

    private String topoDiscoTopic;

    /*
      * IOFSwitchListener methods
      */

    private static org.openkilda.messaging.info.event.PortChangeType toJsonType(PortChangeType type) {
        switch (type) {
            case ADD:
                return org.openkilda.messaging.info.event.PortChangeType.ADD;
            case OTHER_UPDATE:
                return org.openkilda.messaging.info.event.PortChangeType.OTHER_UPDATE;
            case DELETE:
                return org.openkilda.messaging.info.event.PortChangeType.DELETE;
            case UP:
                return org.openkilda.messaging.info.event.PortChangeType.UP;
            default:
                return org.openkilda.messaging.info.event.PortChangeType.DOWN;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NewCorrelationContextRequired
    public void switchAdded(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchService.getSwitch(switchId), SwitchState.ADDED);
        kafkaProducer.postMessage(topoDiscoTopic, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NewCorrelationContextRequired
    public void switchRemoved(final DatapathId switchId) {
        switchManager.stopSafeMode(switchId);
        Message message = buildSwitchMessage(switchId, SwitchState.REMOVED);
        kafkaProducer.postMessage(topoDiscoTopic, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NewCorrelationContextRequired
    public void switchActivated(final DatapathId switchId) {
        final IOFSwitch sw = switchService.getSwitch(switchId);
        logger.info("ACTIVATING SWITCH: {}", switchId);

//        Message message = buildExtendedSwitchMessage(sw, SwitchState.ACTIVATED, switchManager.dumpFlowTable(switchId));
//        kafkaProducer.postMessage(topoDiscoTopic, message);
        ConnectModeRequest.Mode mode = switchManager.connectMode(null);

        try {
            if (mode == ConnectModeRequest.Mode.SAFE) {
                // the bulk of work below is done as part of the safe protocol
                switchManager.startSafeMode(switchId);
                return;
            }

            switchManager.sendSwitchActivate(sw);
            if (mode == ConnectModeRequest.Mode.AUTO) {
                switchManager.installDefaultRules(switchId);
            }

            // else MANUAL MODE - Don't install default rules. NB: without the default rules,
            // ISL discovery will fail.
            switchManager.sendPortUpEvents(sw);
        } catch (SwitchOperationException e) {
            logger.error("Could not activate switch={}", switchId, e);
        }

    }

    public static boolean isPhysicalPort(OFPort p) {
        return !(p.equals(OFPort.LOCAL) ||
                p.equals(OFPort.ALL) ||
                p.equals(OFPort.CONTROLLER) ||
                p.equals(OFPort.ANY) ||
                p.equals(OFPort.FLOOD) ||
                p.equals(OFPort.ZERO) ||
                p.equals(OFPort.NO_MASK) ||
                p.equals(OFPort.IN_PORT) ||
                p.equals(OFPort.NORMAL) ||
                p.equals(OFPort.TABLE));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NewCorrelationContextRequired
    public void switchPortChanged(final DatapathId switchId, final OFPortDesc port, final PortChangeType type) {
        if (isPhysicalPort(port.getPortNo())) {
            Message message = buildPortMessage(switchId, port, type);
            kafkaProducer.postMessage(topoDiscoTopic, message);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NewCorrelationContextRequired
    public void switchChanged(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchService.getSwitch(switchId), SwitchState.CHANGED);
        kafkaProducer.postMessage(topoDiscoTopic, message);
    }

    /*
     * IFloodlightModule methods.
     */

    /**
     * {@inheritDoc}
     */
    @Override
    @NewCorrelationContextRequired
    public void switchDeactivated(final DatapathId switchId) {
        switchManager.stopSafeMode(switchId);
        Message message = buildSwitchMessage(switchId, SwitchState.DEACTIVATED);
        kafkaProducer.postMessage(topoDiscoTopic, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(1);
        services.add(SwitchEventCollector.class);
        return services;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<>();
        map.put(SwitchEventCollector.class, this);
        return map;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(3);
        services.add(IOFSwitchService.class);
        services.add(KafkaMessageProducer.class);
        services.add(ISwitchManager.class);
        return services;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        switchService = context.getServiceImpl(IOFSwitchService.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
        switchManager = context.getServiceImpl(ISwitchManager.class);

        ConfigurationProvider provider = ConfigurationProvider.of(context, this);
        KafkaTopicsConfig topicsConfig = provider.getConfiguration(KafkaTopicsConfig.class);
        topoDiscoTopic = topicsConfig.getTopoDiscoTopic();
    }

    /*
     * Utility functions
     */

    /**
     * {@inheritDoc}
     */
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting " + SwitchEventCollector.class.getCanonicalName());
        switchService.addOFSwitchListener(this);
    }

    /**
     * Builds a switch message type.
     *
     * @param sw        switch instance
     * @param eventType type of event
     * @return Message
     */
    public static Message buildSwitchMessage(final IOFSwitch sw, final SwitchState eventType) {
        return buildMessage(IOFSwitchConverter.buildSwitchInfoData(sw, eventType));
    }

    /**
     * Builds a switch message type with flows.
     *
     * @param sw        switch instance.
     * @param eventType type of event.
     * @param flowStats flows one the switch.
     * @return Message
     */
    private Message buildExtendedSwitchMessage(final IOFSwitch sw, final SwitchState eventType,
            OFFlowStatsReply flowStats) {
        return buildMessage(IOFSwitchConverter.buildSwitchInfoDataExtended(sw, eventType, flowStats));
    }

    /**
     * Builds a switch message type.
     *
     * @param switchId  switch id
     * @param eventType type of event
     * @return Message
     */
    public static Message buildSwitchMessage(final DatapathId switchId, final SwitchState eventType) {
        final String unknown = "unknown";

        InfoData data = new SwitchInfoData(switchId.toString(), eventType, unknown, unknown, unknown, unknown);

        return buildMessage(data);
    }

    /**
     * Builds a generic message object.
     *
     * @param data data to use in the message body
     * @return Message
     */
    public static Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), CorrelationContext.getId(), null);
    }

    /**
     * Builds a port state change message with port number.
     *
     * @param switchId datapathId of switch
     * @param port     port that triggered the event
     * @param type     type of port event
     * @return Message
     */
    public static Message buildPortMessage(final DatapathId switchId, final OFPort port, final PortChangeType type) {
        InfoData data = new PortInfoData(switchId.toString(), port.getPortNumber(), null, toJsonType(type));
        return buildMessage(data);
    }

    /**
     * Builds a port state message with OFPortDesc.
     *
     * @param switchId datapathId of switch
     * @param port     port that triggered the event
     * @param type     type of port event
     * @return Message
     */
    private Message buildPortMessage(final DatapathId switchId, final OFPortDesc port, final PortChangeType type) {
        InfoData data = new PortInfoData(switchId.toString(), port.getPortNo().getPortNumber(), null, toJsonType(type));
        return buildMessage(data);
    }
}
