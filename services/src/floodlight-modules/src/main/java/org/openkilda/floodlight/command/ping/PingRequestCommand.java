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

package org.openkilda.floodlight.command.ping;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.error.OfBatchException;
import org.openkilda.floodlight.error.PingImpossibleException;
import org.openkilda.floodlight.model.OfRequestResponse;
import org.openkilda.floodlight.model.PingData;
import org.openkilda.floodlight.service.batch.OfBatchService;
import org.openkilda.floodlight.service.ping.PingService;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.Ping.Errors;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.OFMessageUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class PingRequestCommand extends Abstract {
    private static Logger log = LoggerFactory.getLogger(PingRequestCommand.class);

    private final Ping ping;

    private final ScheduledExecutorService scheduler;
    private final IOFSwitchService switchService;

    private final OfBatchService batchService;

    public PingRequestCommand(CommandContext context, Ping ping) {
        super(context);

        this.ping = ping;

        FloodlightModuleContext moduleContext = context.getModuleContext();
        switchService = moduleContext.getServiceImpl(IOFSwitchService.class);
        batchService = moduleContext.getServiceImpl(OfBatchService.class);
        scheduler = moduleContext.getServiceImpl(IThreadPoolService.class).getScheduledExecutor();
    }

    @Override
    public Command call() throws Exception {
        try {
            checkDestintaion();
            send(checkSource());
        } catch (PingImpossibleException e) {
            log.error(e.getMessage());
            sendErrorResponse(ping.getPingId(), e.getError());
        }
        return null;
    }

    private void checkDestintaion() throws PingImpossibleException {
        final String destId = ping.getDest().getDatapath();
        IOFSwitch destSw = lookupSwitch(destId);
        if (destSw == null) {
            log.debug("Do not own ping\'s destination switch {}", destId);
            // TODO(surabujin): must be changed when multi FL design will be accepted
            throw new PingImpossibleException(ping, Errors.DEST_NOT_AVAILABLE);
        }

        checkCapability(destSw);
    }

    private IOFSwitch checkSource() throws PingImpossibleException {
        String swId = ping.getSource().getDatapath();
        IOFSwitch sw = lookupSwitch(swId);
        if (sw == null) {
            log.debug("Do not own ping's source switch {}", swId);
            // TODO(surabujin): must be changed when multi FL design will be accepted
            throw new PingImpossibleException(ping, Errors.SOURCE_NOT_AVAILABLE);
        }

        checkCapability(sw);

        return sw;
    }

    private void send(IOFSwitch sw) {
        PingData data = PingData.of(ping);
        data.setSenderLatency(sw.getLatency().getValue());

        PingService pingService = getPingService();
        byte[] signedData = pingService.getSignature().sign(data);
        byte[] rawPackage = pingService.wrapData(ping, signedData).serialize();
        OFMessage message = makePacketOut(sw, rawPackage);

        logPing.info("Send ping {}", ping);
        final CompletableFuture<List<OfRequestResponse>> future = batchService.write(ImmutableList.of(
                new OfRequestResponse(sw.getId(), message)));

        ScheduledFuture<?> timeoutFuture = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                timeout(future);
            }
        }, PingService.SWITCH_PACKET_OUT_TIMEOUT, TimeUnit.MILLISECONDS);

        future.exceptionally(new Function<Throwable, List<OfRequestResponse>>() {
            @Override
            public List<OfRequestResponse> apply(Throwable throwable) {
                exceptional(throwable, timeoutFuture);
                return null;
            }
        });
    }

    void timeout(CompletableFuture<List<OfRequestResponse>> future) {
        if (future.isDone()) {
            return;
        }

        future.cancel(false);

        log.error("Unable to send ping {} - switch I/O timeout ({}ms)", ping, PingService.SWITCH_PACKET_OUT_TIMEOUT);
        sendErrorResponse(ping.getPingId(), Ping.Errors.WRITE_FAILURE);
    }

    void exceptional(Throwable e, ScheduledFuture<?> timeoutFuture) {
        timeoutFuture.cancel(false);

        reportSendError(e);
        sendErrorResponse(ping.getPingId(), Ping.Errors.WRITE_FAILURE);
    }

    private OFMessage makePacketOut(IOFSwitch sw, byte[] data) {
        OFFactory ofFactory = sw.getOFFactory();
        OFPacketOut.Builder pktOut = ofFactory.buildPacketOut();

        pktOut.setData(data);

        List<OFAction> actions = new ArrayList<>(2);
        actions.add(ofFactory.actions().buildOutput().setPort(OFPort.TABLE).build());
        pktOut.setActions(actions);

        OFMessageUtils.setInPort(pktOut, OFPort.of(ping.getSource().getPortNumber()));

        return pktOut.build();
    }

    private void checkCapability(IOFSwitch sw) throws PingImpossibleException {
        if (0 < OFVersion.OF_13.compareTo(sw.getOFFactory().getVersion())) {
            throw new PingImpossibleException(ping, Errors.NOT_CAPABLE);
        }
    }

    private IOFSwitch lookupSwitch(String switchId) {
        DatapathId dpId = DatapathId.of(switchId);
        return switchService.getActiveSwitch(dpId);
    }

    private void reportSendError(Throwable rawException) {
        try {
            throw rawException;
        } catch (OfBatchException e) {
            final String prefix = String.format("Unable to send ping %s", ping);
            boolean isReported = false;
            for (OfRequestResponse error : e.getErrors()) {
                isReported = true;
                log.error("{}: {}", prefix, error);
            }
            if (!isReported) {
                log.error("{}: {}", prefix, e.getMessage());
            }
        } catch (Throwable e) {
            log.error("Unexpected error during switch communication: {}: {}", e.getClass().getName(), e.getMessage());
        }
    }
}
