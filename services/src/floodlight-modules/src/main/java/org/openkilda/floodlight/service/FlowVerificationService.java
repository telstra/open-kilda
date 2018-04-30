/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.floodlight.service;

import org.openkilda.floodlight.SwitchUtils;
import org.openkilda.floodlight.exc.CorruptedNetworkDataException;
import org.openkilda.floodlight.exc.InvalidSignatureConfigurationException;
import org.openkilda.floodlight.model.flow.VerificationData;
import org.openkilda.floodlight.command.flow.VerificationListenCommand;
import org.openkilda.floodlight.command.flow.VerificationSendCommand;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.utils.DataSignature;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class FlowVerificationService extends AbstractOfHandler implements IFloodlightService {
    private static Logger log = LoggerFactory.getLogger(FlowVerificationService.class);

    private final LinkedList<VerificationListenCommand> pendingRecipients = new LinkedList<>();

    private DataSignature signature = null;
    private SwitchUtils switchUtils = null;

    public void subscribe(VerificationListenCommand handler) {
        synchronized (pendingRecipients) {
            pendingRecipients.add(handler);
        }
    }

    public void unsubscribe(VerificationListenCommand handler) {
        synchronized (pendingRecipients) {
            pendingRecipients.remove(handler);
        }
    }

    public void init(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        // FIXME(surabujin): avoid usage foreign module configuration
        Map<String, String> config = moduleContext.getConfigParams(PathVerificationService.class);
        try {
            signature = new DataSignature(config.get("hmac256-secret"));
        } catch (InvalidSignatureConfigurationException e) {
            throw new FloodlightModuleException(String.format("Unable to initialize %s", getClass().getName()), e);
        }

        switchUtils = new SwitchUtils(moduleContext.getServiceImpl(IOFSwitchService.class));
        activateSubscription(moduleContext, OFType.PACKET_IN);
    }

    @Override
    public boolean handle(IOFSwitch sw, OFMessage packet, FloodlightContext context) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(context, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        VerificationData data;
        try {
            byte[] payload = VerificationSendCommand.unwrapData(switchUtils.dpIdToMac(sw), eth);
            if (payload == null) {
                log.debug("packet xId: {} is not flow verification packet", packet.getXid());
                return false;
            }

            DecodedJWT token = signature.verify(payload);
            data = VerificationData.of(token);

            if (! data.getDest().equals(sw.getId())) {
                throw new CorruptedNetworkDataException(String.format(
                        "Catch flow verification package on %s while target is %s", sw.getId(), data.getDest()));
            }
        } catch (CorruptedNetworkDataException e) {
            log.error(String.format("dpid:%s %s", sw.getId(), e));
            return false;
        }

        boolean isHandled = false;
        synchronized (pendingRecipients) {
            for (ListIterator<VerificationListenCommand> iter = pendingRecipients.listIterator(); iter.hasNext(); ) {
                VerificationListenCommand command = iter.next();

                if (!command.packetIn(sw, data)) {
                    continue;
                }
                isHandled = true;
                iter.remove();
                break;
            }
        }

        return isHandled;
    }

    @Override
    protected Set<String> mustHandleBefore() {
        return ImmutableSet.of("PathVerificationService");
    }

    public DataSignature getSignature() {
        return signature;
    }
}
