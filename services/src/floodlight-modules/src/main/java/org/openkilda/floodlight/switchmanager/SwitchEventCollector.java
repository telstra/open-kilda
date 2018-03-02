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

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Topic;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SwitchEventCollector implements IFloodlightModule, IOFSwitchListener, IFloodlightService {
    private static final Logger logger = LoggerFactory.getLogger(SwitchEventCollector.class);
    private static final String TOPO_EVENT_TOPIC = Topic.TOPO_DISCO;
    private IOFSwitchService switchService;
    private KafkaMessageProducer kafkaProducer;
    private ISwitchManager switchManager;

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
    public void switchAdded(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchService.getSwitch(switchId), SwitchState.ADDED);
        kafkaProducer.postMessage(TOPO_EVENT_TOPIC, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchRemoved(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchState.REMOVED);
        kafkaProducer.postMessage(TOPO_EVENT_TOPIC, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchActivated(final DatapathId switchId) {
        final IOFSwitch sw = switchService.getSwitch(switchId);

        Message message = buildSwitchMessage(sw, SwitchState.ACTIVATED);
        kafkaProducer.postMessage(TOPO_EVENT_TOPIC, message);

        try {
            switchManager.installDefaultRules(switchId);
        } catch (SwitchOperationException e) {
            logger.error("Could not activate switch={}", switchId);
        }

        if (sw.getEnabledPortNumbers() != null) {
            for (OFPort p : sw.getEnabledPortNumbers()) {
                kafkaProducer.postMessage(TOPO_EVENT_TOPIC, buildPortMessage(sw.getId(), p, PortChangeType.UP));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchPortChanged(final DatapathId switchId, final OFPortDesc port, final PortChangeType type) {
        Message message = buildPortMessage(switchId, port, type);
        kafkaProducer.postMessage(TOPO_EVENT_TOPIC, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchChanged(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchService.getSwitch(switchId), SwitchState.CHANGED);
        kafkaProducer.postMessage(TOPO_EVENT_TOPIC, message);
    }

    /*
     * IFloodlightModule methods.
     */

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchDeactivated(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchId, SwitchState.DEACTIVATED);
        kafkaProducer.postMessage(TOPO_EVENT_TOPIC, message);
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
     * Builds a SwitchInfoData from IOFSwitch.
     *
     * @param sw        switch instance
     * @param eventType type of event
     * @return Message
     */
    public static SwitchInfoData buildSwitchInfoData(IOFSwitch sw, SwitchState eventType) {
        String switchId = sw.getId().toString();
        InetSocketAddress address = (InetSocketAddress) sw.getInetAddress();
        InetSocketAddress controller =(InetSocketAddress) sw.getConnectionByCategory(
                LogicalOFMessageCategory.MAIN).getRemoteInetAddress();

        return new SwitchInfoData(
                switchId,
                eventType,
                String.format("%s:%d",
                        address.getHostString(),
                        address.getPort()),
                address.getHostName(),
                String.format("%s %s %s",
                        sw.getSwitchDescription().getManufacturerDescription(),
                        sw.getOFFactory().getVersion().toString(),
                        sw.getSwitchDescription().getSoftwareDescription()),
                controller.getHostString());
    }

    /**
     * Builds a switch message type.
     *
     * @param sw        switch instance
     * @param eventType type of event
     * @return Message
     */
    private Message buildSwitchMessage(final IOFSwitch sw, final SwitchState eventType) {
        return buildMessage(buildSwitchInfoData(sw, eventType));
    }

    /**
     * Builds a switch message type.
     *
     * @param switchId  switch id
     * @param eventType type of event
     * @return Message
     */
    private Message buildSwitchMessage(final DatapathId switchId, final SwitchState eventType) {
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
    private Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), "system", null);
    }

    /**
     * Builds a port state change message with port number.
     *
     * @param switchId datapathId of switch
     * @param port     port that triggered the event
     * @param type     type of port event
     * @return Message
     */
    private Message buildPortMessage(final DatapathId switchId, final OFPort port, final PortChangeType type) {
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
