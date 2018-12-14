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

import org.openkilda.floodlight.converter.IofSwitchConverter;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpBeginMarker;
import org.openkilda.messaging.info.discovery.NetworkDumpEndMarker;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class SwitchTrackingService implements IOFSwitchListener, IService {
    private static final Logger logger = LoggerFactory.getLogger(SwitchTrackingService.class);

    private final ReadWriteLock discoveryLock = new ReentrantReadWriteLock();

    private IKafkaProducerService producerService;
    private ISwitchManager switchManager;
    private FeatureDetectorService featureDetector;

    private String discoveryTopic;

    /**
     * Send dump contain all connected at this moment switches.
     */
    public void dumpAllSwitches(String correlationId) {
        discoveryLock.writeLock().lock();
        try {
            dumpAllSwitchesAction(correlationId);
        } finally {
            discoveryLock.writeLock().unlock();
        }
    }

    /**
     * Desired to be used on the end of switch activation to send discovery messages.
     */
    public void completeSwitchActivation(DatapathId dpId) {
        discoveryLock.readLock().lock();
        try {
            switchDiscoveryAction(dpId, SwitchChangeType.ACTIVATED);
        } finally {
            discoveryLock.readLock().unlock();
        }
    }

    @Override
    @NewCorrelationContextRequired
    public void switchAdded(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.ADDED);
        switchDiscovery(switchId, SwitchChangeType.ADDED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchRemoved(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.REMOVED);

        // TODO(surabujin): must figure out events order/set during lost connection
        switchManager.deactivate(switchId);
        switchDiscovery(switchId, SwitchChangeType.REMOVED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchActivated(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.ACTIVATED);

        try {
            switchManager.activate(switchId);
        } catch (SwitchOperationException e) {
            logger.error("OF switch event ({} - {}): {}", switchId, SwitchChangeType.ACTIVATED, e.getMessage());
        }
    }

    @Override
    @NewCorrelationContextRequired
    public void switchDeactivated(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.DEACTIVATED);

        switchManager.deactivate(switchId);
        switchDiscovery(switchId, SwitchChangeType.DEACTIVATED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchChanged(final DatapathId switchId) {
        logSwitchEvent(switchId, SwitchChangeType.CHANGED);
        switchDiscovery(switchId, SwitchChangeType.CHANGED);
    }

    @Override
    @NewCorrelationContextRequired
    public void switchPortChanged(final DatapathId switchId, final OFPortDesc portDesc, final PortChangeType type) {
        OFPort port = portDesc.getPortNo();
        logPortEvent(switchId, port, type);

        if (ISwitchManager.isPhysicalPort(portDesc)) {
            portDiscovery(switchId, port, type);
        }
    }

    @Override
    public void setup(FloodlightModuleContext context) {
        producerService = context.getServiceImpl(IKafkaProducerService.class);
        switchManager = context.getServiceImpl(ISwitchManager.class);
        featureDetector = context.getServiceImpl(FeatureDetectorService.class);

        discoveryTopic = context.getServiceImpl(KafkaUtilityService.class).getTopics().getTopoDiscoTopic();

        context.getServiceImpl(IOFSwitchService.class).addOFSwitchListener(this);
    }

    private void dumpAllSwitchesAction(String correlationId) {
        producerService.enableGuaranteedOrder(discoveryTopic);
        try {
            producerService.sendMessageAndTrack(
                    discoveryTopic,
                    new InfoMessage(new NetworkDumpBeginMarker(), System.currentTimeMillis(), correlationId));

            for (IOFSwitch sw : switchManager.getAllSwitchMap().values()) {
                NetworkDumpSwitchData swData = new NetworkDumpSwitchData(buildSwitch(sw));
                producerService.sendMessageAndTrack(discoveryTopic,
                                                    new InfoMessage(swData, System.currentTimeMillis(), correlationId));
            }

            producerService.sendMessageAndTrack(
                    discoveryTopic,
                    new InfoMessage(new NetworkDumpEndMarker(), System.currentTimeMillis(), correlationId));
        } finally {
            producerService.disableGuaranteedOrder(discoveryTopic);
        }
    }

    private void switchDiscovery(DatapathId dpId, SwitchChangeType state) {
        discoveryLock.readLock().lock();
        try {
            switchDiscoveryAction(dpId, state);
        } finally {
            discoveryLock.readLock().unlock();
        }
    }

    private void portDiscovery(DatapathId dpId, OFPort port, PortChangeType changeType) {
        discoveryLock.readLock().lock();
        try {
            portDiscoveryAction(dpId, port, changeType);
        } finally {
            discoveryLock.readLock().unlock();
        }
    }

    private void switchDiscoveryAction(DatapathId dpId, SwitchChangeType event) {
        logger.info("Send switch discovery ({} - {})", dpId, event);
        Message message = null;
        if (SwitchChangeType.DEACTIVATED != event && SwitchChangeType.REMOVED != event) {
            try {
                IOFSwitch sw = switchManager.lookupSwitch(dpId);
                SpeakerSwitchView switchView = buildSwitch(sw);
                message = buildSwitchMessage(sw, switchView, event);
            } catch (SwitchNotFoundException e) {
                logger.error(
                        "Switch {} is not in management state now({}), switch ISL discovery details will be degraded.",
                        dpId, e.getMessage());
            }
        }
        if (message == null) {
            message = buildSwitchMessage(dpId, event);
        }

        producerService.sendMessageAndTrack(discoveryTopic, dpId.toString(), message);
    }

    private void portDiscoveryAction(DatapathId dpId, OFPort port, PortChangeType changeType) {
        logger.info("Send port discovery ({}-{} - {})", dpId, port, changeType);
        Message message = buildPortMessage(dpId, port, changeType);
        producerService.sendMessageAndTrack(discoveryTopic, dpId.toString(), message);
    }

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
     * Builds fully filled switch ISL discovery message.
     *
     * @param sw switch instance
     * @param eventType type of event
     * @return Message
     */
    private static Message buildSwitchMessage(IOFSwitch sw, SpeakerSwitchView switchView, SwitchChangeType eventType) {
        return buildMessage(IofSwitchConverter.buildSwitchInfoData(sw, switchView, eventType));
    }

    /**
     * Builds degraded switch ISL discovery message.
     *
     * @param dpId switch datapath
     * @param eventType type of event
     * @return Message
     */
    private static Message buildSwitchMessage(DatapathId dpId, SwitchChangeType eventType) {
        return buildMessage(new SwitchInfoData(new SwitchId(dpId.getLong()), eventType));
    }

    /**
     * Builds a port state change message with port number.
     *
     * @param switchId datapathId of switch
     * @param port port that triggered the event
     * @param type type of port event
     * @return Message
     */
    private static Message buildPortMessage(final DatapathId switchId, final OFPort port, final PortChangeType type) {
        InfoData data = new PortInfoData(new SwitchId(switchId.getLong()), port.getPortNumber(),
                null, toJsonType(type));
        return buildMessage(data);
    }

    /**
     * Builds a generic message object.
     *
     * @param data data to use in the message body
     * @return Message
     */
    private static Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), CorrelationContext.getId(), null);
    }

    private SpeakerSwitchView buildSwitch(IOFSwitch sw) {
        List<SpeakerSwitchPortView> ports = switchManager.getPhysicalPorts(sw).stream()
                .map(port -> new SpeakerSwitchPortView(
                        port.getPortNo().getPortNumber(),
                        port.isEnabled()
                                ? SpeakerSwitchPortView.State.UP
                                : SpeakerSwitchPortView.State.DOWN))
                .collect(Collectors.toList());
        Set<SpeakerSwitchView.Feature> features = featureDetector.detectSwitch(sw);
        return new SpeakerSwitchView(new SwitchId(sw.getId().getLong()), features, ports);
    }

    private void logSwitchEvent(DatapathId dpId, SwitchChangeType event) {
        logger.info("OF switch event ({} - {})", dpId, event);
    }

    private void logPortEvent(DatapathId dpId, OFPort port, PortChangeType changeType) {
        logger.info("OF port event ({}-{} - {})", dpId, port, changeType);
    }
}
