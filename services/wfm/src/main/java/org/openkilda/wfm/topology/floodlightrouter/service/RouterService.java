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

import org.openkilda.messaging.AliveResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;

import lombok.extern.slf4j.Slf4j;

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
        emitAliveRequests(routerMessageSender);
        floodlightTracker.handleAliveExpiration(routerMessageSender);
    }

    /**
     * Process response from speaker disco.
     * @param routerMessageSender callback to be used for message sending
     * @param message message to be handled and resend
     */
    public void processSpeakerDiscoResponse(MessageSender routerMessageSender, Message message) {
        if (message instanceof InfoMessage) {
            InfoMessage infoMessage = (InfoMessage) message;
            InfoData infoData = infoMessage.getData();
            SwitchId switchId = null;
            String region = infoMessage.getRegion();
            handleResponseFromSpeaker(routerMessageSender, region, message.getTimestamp());
            if (infoData instanceof AliveResponse) {
                AliveResponse aliveResponse = (AliveResponse) infoData;
                if (aliveResponse.getFailedMessages() > 0) {
                    routerMessageSender.emitNetworkDumpRequest(region);
                }
                return;
            } else if (infoData instanceof IslInfoData) {
                IslInfoData isl = (IslInfoData) infoData;
                log.debug("Processing disco response {}", isl.getPacketId());
            } else if (infoData instanceof NetworkDumpSwitchData) {
                switchId = ((NetworkDumpSwitchData) infoData).getSwitchView().getDatapath();
            } else if (infoData instanceof SwitchInfoData) {
                switchId = ((SwitchInfoData) infoData).getSwitchId();
            } else if (infoData instanceof PortInfoData) {
                switchId = ((PortInfoData) infoData).getSwitchId();
            }

            // NOTE(tdurakov): need to notify of a mapping update
            if (switchId != null && region != null && floodlightTracker.updateSwitchRegion(switchId, region)) {
                routerMessageSender.emitRegionNotification(new SwitchMapping(switchId, region));
            }
        }
        routerMessageSender.emitControllerMessage(message);
    }

    /**
     * Process request to speaker disco.
     * @param routerMessageSender callback to be used for message sending
     * @param message message to be handled and resend
     */
    public void processDiscoSpeakerRequest(MessageSender routerMessageSender, Message message) {
        SwitchId switchId = RouterUtils.lookupSwitchId(message);
        if (switchId != null) {
            String region = floodlightTracker.lookupRegion(switchId);
            if (region == null) {
                log.error("Received command message for the untracked switch: {} {}", switchId, message);
            } else {
                routerMessageSender.emitSpeakerMessage(message, region);
            }
        } else {
            log.warn("Received message without target switch from SPEAKER_DISCO stream: {}", message);
        }
    }

    private void handleResponseFromSpeaker(MessageSender routerMessageSender, String region, long timestamp) {
        boolean requireSync = floodlightTracker.handleAliveResponse(region, timestamp);
        if (requireSync) {
            log.info("Region {} requires sync", region);
            routerMessageSender.emitNetworkDumpRequest(region);
        }
    }

    private void emitAliveRequests(MessageSender messageSender) {
        for (String region : floodlightTracker.getRegionsForAliveRequest()) {
            messageSender.emitSpeakerAliveRequest(region);
        }
    }
}
