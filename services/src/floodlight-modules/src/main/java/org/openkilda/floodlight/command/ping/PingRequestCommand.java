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
import org.openkilda.floodlight.error.PingImpossibleException;
import org.openkilda.floodlight.model.PingData;
import org.openkilda.floodlight.service.ping.PingService;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.Ping.Errors;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
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

import java.util.Collections;
import java.util.List;

public class PingRequestCommand extends PingCommand {
    private static Logger log = LoggerFactory.getLogger(PingRequestCommand.class);

    private final Ping ping;

    private final IOFSwitchService switchService;

    public PingRequestCommand(CommandContext context, Ping ping) {
        super(context);

        this.ping = ping;

        FloodlightModuleContext moduleContext = context.getModuleContext();
        switchService = moduleContext.getServiceImpl(IOFSwitchService.class);
    }

    @Override
    public Command call() {
        try {
            checkDestination();
            send(checkSource());
        } catch (PingImpossibleException e) {
            log.error(e.getMessage());
            sendErrorResponse(ping.getPingId(), e.getError());
        }
        return null;
    }

    private void checkDestination() throws PingImpossibleException {
        SwitchId destId = ping.getDest().getDatapath();
        IOFSwitch destSw = lookupSwitch(destId);
        if (destSw == null) {
            log.debug("Do not own ping\'s destination switch {}", destId);
            // TODO(surabujin): must be changed when multi FL design will be accepted
            throw new PingImpossibleException(ping, Errors.DEST_NOT_AVAILABLE);
        }

        checkCapability(destSw);
    }

    private IOFSwitch checkSource() throws PingImpossibleException {
        SwitchId swId = ping.getSource().getDatapath();
        IOFSwitch sw = lookupSwitch(swId);
        if (sw == null) {
            log.debug("Do not own ping's source switch {}", swId);
            // TODO(surabujin): must be changed when multi FL design will be accepted
            throw new PingImpossibleException(ping, Errors.SOURCE_NOT_AVAILABLE);
        }

        checkCapability(sw);

        return sw;
    }

    private void send(IOFSwitch sw) throws PingImpossibleException {
        PingData data = PingData.of(ping);
        data.setSenderLatency(sw.getLatency().getValue());

        PingService pingService = getPingService();
        byte[] signedData = pingService.getSignature().sign(data);
        byte[] rawPackage = pingService.wrapData(ping, signedData).serialize();
        OFMessage message = makePacketOut(sw, rawPackage);

        if (!sw.write(message)) {
            throw new PingImpossibleException(ping, Errors.WRITE_FAILURE);
        }
        logPing.info("Send ping {}", ping);
    }

    private OFMessage makePacketOut(IOFSwitch sw, byte[] data) {
        OFFactory ofFactory = sw.getOFFactory();
        OFPacketOut.Builder pktOut = ofFactory.buildPacketOut();

        pktOut.setData(data);

        List<OFAction> actions = Collections.singletonList(ofFactory.actions().buildOutput()
                .setPort(OFPort.TABLE)
                .build());
        pktOut.setActions(actions);

        OFMessageUtils.setInPort(pktOut, OFPort.of(ping.getSource().getPortNumber()));

        return pktOut.build();
    }

    private void checkCapability(IOFSwitch sw) throws PingImpossibleException {
        if (0 < OFVersion.OF_13.compareTo(sw.getOFFactory().getVersion())) {
            throw new PingImpossibleException(ping, Errors.NOT_CAPABLE);
        }
    }

    private IOFSwitch lookupSwitch(SwitchId switchId) {
        DatapathId dpId = DatapathId.of(switchId.toLong());
        return switchService.getActiveSwitch(dpId);
    }
}
