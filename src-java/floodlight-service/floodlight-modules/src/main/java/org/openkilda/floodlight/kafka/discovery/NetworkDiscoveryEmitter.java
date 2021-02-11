/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.kafka.discovery;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.model.SwitchId;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;

@Slf4j
public class NetworkDiscoveryEmitter {
    private final Clock clock;
    private final Duration flushDelay;

    private final String confirmationTopic;
    private final String region;

    private final IKafkaProducerService kafkaProducerService;
    private final IPathVerificationService pathVerificationService;

    private final LinkedHashMap<Target, DiscoveryEmitterAction> tracking = new LinkedHashMap<>();

    public NetworkDiscoveryEmitter(FloodlightModuleContext moduleContext, Duration flushDelay) {
        this(Clock.systemUTC(), moduleContext, flushDelay);
    }

    public NetworkDiscoveryEmitter(Clock clock, FloodlightModuleContext moduleContext, Duration flushDelay) {
        this.clock = clock;
        this.flushDelay = flushDelay;

        KafkaChannel kafkaChannel = moduleContext.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
        confirmationTopic = kafkaChannel.getTopoDiscoTopic();
        region = kafkaChannel.getRegion();

        kafkaProducerService = moduleContext.getServiceImpl(IKafkaProducerService.class);
        pathVerificationService = moduleContext.getServiceImpl(IPathVerificationService.class);
    }

    /**
     * Handle discovery request. Make a decision is it can be processed immediately or should be postponed.
     */
    public void handleRequest(DiscoverIslCommandData request, String correlationId) {
        Target target = Target.of(request);

        DiscoveryHolder discovery = new DiscoveryHolder(correlationId, request);
        // To put new action at the end of tracing iteration list we must perform remove and put actions.
        DiscoveryEmitterAction replacement;
        synchronized (tracking) {
            DiscoveryEmitterAction current = tracking.remove(target);
            replacement = selectAction(current, discovery);
            tracking.put(target, replacement);
        }

        replacement.perform(this);
    }

    /**
     * Checks postponed request timeout value and `flush` these ones who have reached it.
     */
    public void tick() {
        Instant now = clock.instant();
        LinkedList<DiscoveryEmitterAction> toFlush = new LinkedList<>();
        synchronized (tracking) {
            Set<Entry<Target, DiscoveryEmitterAction>> entries = tracking.entrySet();
            Iterator<Entry<Target, DiscoveryEmitterAction>> iter = entries.iterator();
            while (iter.hasNext()) {
                DiscoveryEmitterAction action = iter.next().getValue();
                if (now.isBefore(action.getExpireTime())) {
                    break;
                }

                toFlush.addLast(action);
                iter.remove();
            }
        }

        for (DiscoveryEmitterAction action : toFlush) {
            action.flush(this);
        }
    }

    void emit(DiscoveryHolder discovery) {
        DiscoverIslCommandData request = discovery.getDiscoveryRequest();
        DatapathId dpId = DatapathId.of(request.getSwitchId().getId());
        pathVerificationService.sendDiscoveryMessage(dpId, OFPort.of(request.getPortNumber()), request.getPacketId());

        DiscoPacketSendingConfirmation confirmation = new DiscoPacketSendingConfirmation(
                new NetworkEndpoint(request.getSwitchId(), request.getPortNumber()), request.getPacketId());
        kafkaProducerService.sendMessageAndTrackWithZk(confirmationTopic, request.getSwitchId().toString(),
                new InfoMessage(confirmation, System.currentTimeMillis(), discovery.getCorrelationId(), region));
    }

    void suppress(DiscoveryHolder discovery) {
        DiscoverIslCommandData request = discovery.getDiscoveryRequest();
        log.warn("suppress discovery package for: {}-{} id:{}",
                request.getSwitchId(), request.getPortNumber(), request.getPacketId());
    }

    private DiscoveryEmitterAction selectAction(DiscoveryEmitterAction current, DiscoveryHolder discovery) {
        Instant expireAt = clock.instant().plus(flushDelay);
        if (current == null) {
            return new DiscoveryEmitterImmediateAction(expireAt, discovery);
        }
        return new DiscoveryEmitterPostponedAction(expireAt, discovery, current);
    }

    @Value
    private static class Target {
        SwitchId switchId;
        int portNumber;

        public static Target of(DiscoverIslCommandData request) {
            return new Target(request.getSwitchId(), request.getPortNumber());
        }
    }
}
