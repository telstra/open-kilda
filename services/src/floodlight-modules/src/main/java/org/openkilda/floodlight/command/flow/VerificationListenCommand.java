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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.SwitchUtils;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.error.InsufficientCapabilitiesException;
import org.openkilda.floodlight.model.flow.VerificationData;
import org.openkilda.floodlight.service.FlowVerificationService;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.info.flow.FlowVerificationErrorCode;
import org.openkilda.messaging.info.flow.UniFlowVerificationResponse;
import org.openkilda.messaging.info.flow.VerificationMeasures;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class VerificationListenCommand extends AbstractVerificationCommand {
    private static final Logger log = LoggerFactory.getLogger(VerificationListenCommand.class);

    private final VerificationData verificationData;

    private final FlowVerificationService flowVerificationService;
    private final IThreadPoolService scheduler;

    public VerificationListenCommand(CommandContext context, UniFlowVerificationRequest verificationRequest)
            throws InsufficientCapabilitiesException {
        super(context, verificationRequest);

        this.verificationData = VerificationData.of(verificationRequest);

        FloodlightModuleContext moduleContext = getContext().getModuleContext();
        flowVerificationService = moduleContext.getServiceImpl(FlowVerificationService.class);
        scheduler = moduleContext.getServiceImpl(IThreadPoolService.class);

        checkCapabilities(new SwitchUtils(moduleContext.getServiceImpl(IOFSwitchService.class)));
    }

    @Override
    public void run() {
        flowVerificationService.subscribe(this);

        TimeoutNotification notification = new TimeoutNotification(this);
        scheduler.getScheduledExecutor().schedule(
                notification, getVerificationRequest().getTimeout(), TimeUnit.MILLISECONDS);
    }

    /**
     * Process received network packet.
     */
    public boolean packetIn(IOFSwitch sw, VerificationData payload) {
        if (! verificationData.equals(payload)) {
            return false;
        }

        VerificationMeasures measures = payload.produceMeasurements(sw.getLatency().getValue());
        log.debug(
                "Receive flow VERIFICATION package - packetId: {}, latency: {}",
                payload.getPacketId(), measures.getNetworkLatency());
        UniFlowVerificationResponse response = new UniFlowVerificationResponse(getVerificationRequest(), measures);
        sendResponse(response);

        return true;
    }

    private void timeout() {
        log.error("Give up waiting for flow VERIFICATION packet (packetId: {})", verificationData.getPacketId());

        flowVerificationService.unsubscribe(this);
        sendErrorResponse(FlowVerificationErrorCode.TIMEOUT);
    }

    private void checkCapabilities(SwitchUtils switchUtils) throws InsufficientCapabilitiesException {
        DatapathId switchId = DatapathId.of(getVerificationRequest().getDestSwitchId().toLong());
        IOFSwitch sw = switchUtils.lookupSwitch(switchId);

        if (0 < OFVersion.OF_13.compareTo(sw.getOFFactory().getVersion())) {
            throw new InsufficientCapabilitiesException("Destination switch is unable to catch PING package");
        }
    }

    static class TimeoutNotification implements Runnable {
        private final VerificationListenCommand operation;

        TimeoutNotification(VerificationListenCommand command) {
            this.operation = command;
        }

        @Override
        public void run() {
            operation.timeout();
        }
    }
}
