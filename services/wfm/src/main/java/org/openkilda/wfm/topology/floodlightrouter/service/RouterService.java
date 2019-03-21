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

package org.openkilda.wfm.topology.floodlightrouter.service;

import org.openkilda.messaging.AliveRequest;
import org.openkilda.messaging.AliveResponse;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.Stream;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class RouterService {

    private final FloodlightTracker floodlightTracker;

    public RouterService(FloodlightTracker floodlightTracker) {
        this.floodlightTracker = floodlightTracker;
    }

    /**
     * Process periodic state update.
     * @param routerMessageSender callback to be used for message sending
     */
    public void doPeriodicProcessing(MessageSender routerMessageSender) {
        for (String region : floodlightTracker.getRegionsForAliveRequest()) {
            AliveRequest request = new AliveRequest();
            CommandMessage message = new CommandMessage(request, System.currentTimeMillis(), UUID.randomUUID()
                    .toString());
            routerMessageSender.send(message.getCorrelationId(), message,
                                     Stream.formatWithRegion(Stream.SPEAKER_DISCO, region));

        }
        floodlightTracker.checkTimeouts();
        floodlightTracker.handleUnmanagedSwitches(routerMessageSender);
    }

    /**
     * Process response from speaker disco.
     * @param routerMessageSender callback to be used for message sending
     * @param message message to be handled and resend
     */
    public void processSpeakerDiscoResponse(MessageSender routerMessageSender,
                                            Message message) {
        if (message instanceof InfoMessage) {
            InfoMessage infoMessage = (InfoMessage) message;
            InfoData infoData = infoMessage.getData();
            SwitchId switchId = null;
            String region = infoMessage.getRegion();
            handleResponseFromSpeaker(routerMessageSender, region, message.getTimestamp());
            if (infoData instanceof AliveResponse) {
                AliveResponse aliveResponse = (AliveResponse) infoData;
                if (aliveResponse.getFailedMessages() > 0) {
                    sendNetworkRequest(routerMessageSender, region);
                }
                return;
            } else if (infoData instanceof NetworkDumpSwitchData) {
                switchId = ((NetworkDumpSwitchData) infoData).getSwitchView().getDatapath();
            } else if (infoData instanceof SwitchInfoData) {
                switchId = ((SwitchInfoData) infoData).getSwitchId();
            } else if (infoData instanceof PortInfoData) {
                switchId = ((PortInfoData) infoData).getSwitchId();
            }

            // NOTE(tdurakov): need to notify of a mapping update
            if (switchId != null && region != null && floodlightTracker.updateSwitchRegion(switchId, region)) {
                routerMessageSender.send(new SwitchMapping(switchId, region), Stream.REGION_NOTIFICATION);
            }
        }
        routerMessageSender.send(message, Stream.KILDA_TOPO_DISCO);
    }




    /**
     * Process request to speaker disco.
     * @param routerMessageSender callback to be used for message sending
     * @param message message to be handled and resend
     */
    public void processDiscoSpeakerRequest(MessageSender routerMessageSender, Message message) {
        SwitchId switchId = RouterUtils.lookupSwitchIdInCommandMessage(message);
        if (switchId != null) {
            String region = floodlightTracker.lookupRegion(switchId);
            if (region == null) {
                log.error("Received command message for the untracked switch: {} {}", switchId, message);
            } else {
                String stream = Stream.formatWithRegion(Stream.SPEAKER_DISCO, region);
                routerMessageSender.send(message, stream);
            }
        } else {
            log.warn("Received message without target switch from SPEAKER_DISCO stream: {}", message);
        }
    }

    private void handleResponseFromSpeaker(MessageSender routerMessageSender, String region,
                                           long timestamp) {
        boolean requireSync = floodlightTracker.handleAliveResponse(region, timestamp);
        if (requireSync) {
            log.info("Region {} requires sync", region);
            sendNetworkRequest(routerMessageSender, region);
        }
    }

    /**
     * Send network dump requests for target region.
     * @param routerMessageSender sender
     * @param region target
     * @return requested correlation id
     */
    public String sendNetworkRequest(MessageSender routerMessageSender, String region) {
        String correlationId = UUID.randomUUID().toString();
        CommandMessage command = new CommandMessage(new NetworkCommandData(),
                System.currentTimeMillis(), correlationId,
                Destination.CONTROLLER);

        log.info(
                "Send network dump request (correlation-id: {})",
                correlationId);
        routerMessageSender.send(correlationId, command, Stream.formatWithRegion(Stream.SPEAKER_DISCO, region));
        return correlationId;
    }
}
