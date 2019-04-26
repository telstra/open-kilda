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

package org.openkilda.floodlight.command;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.flow.FlowRemoveCommand;
import org.openkilda.floodlight.command.flow.GetRuleCommand;
import org.openkilda.floodlight.command.flow.InstallEgressRuleCommand;
import org.openkilda.floodlight.command.flow.InstallIngressRuleCommand;
import org.openkilda.floodlight.command.flow.InstallOneSwitchRuleCommand;
import org.openkilda.floodlight.command.flow.InstallTransitRuleCommand;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.SwitchWriteException;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Getter;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@JsonTypeInfo(use = Id.NAME, property = "clazz")
@JsonSubTypes({
        @Type(value = InstallIngressRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.InstallMultiSwitchIngressRule"),
        @Type(value = InstallOneSwitchRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.InstallSingleSwitchIngressRule"),
        @Type(value = InstallTransitRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.InstallTransitRule"),
        @Type(value = InstallEgressRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.InstallEgressRule"),
        @Type(value = FlowRemoveCommand.class,
                name = "org.openkilda.floodlight.flow.request.RemoveRule"),
        @Type(value = GetRuleCommand.class,
                name = "org.openkilda.floodlight.flow.request.GetInstalledRule")
})
@Getter
public abstract class OfCommand {

    protected final SwitchId switchId;
    protected final MessageContext messageContext;

    public OfCommand(SwitchId switchId, MessageContext messageContext) {
        this.switchId = switchId;
        this.messageContext = messageContext;
    }

    /**
     * Helps to execute OF command and handle successful and error responses.
     * @param moduleContext floodlight context.
     * @return response wrapped into completable future.
     */
    public CompletableFuture<FloodlightResponse> execute(FloodlightModuleContext moduleContext) {
        ISwitchManager switchManager = moduleContext.getServiceImpl(ISwitchManager.class);
        SessionService sessionService = moduleContext.getServiceImpl(SessionService.class);
        IOFSwitch sw;
        try {
            DatapathId dpid = DatapathId.of(switchId.toLong());
            sw = switchManager.lookupSwitch(dpid);

            return writeCommands(sw, sessionService, moduleContext)
                    .handle((result, error) -> {
                        if (error != null) {
                            getLogger().error("Error occurred while processing OF command", error);
                            return buildError(error);
                        } else {
                            return result.isPresent() ? buildResponse(result.get()) : buildResponse();
                        }
                    });
        } catch (Exception e) {
            getLogger().error("Failed to execute OF command", e);
            return CompletableFuture.completedFuture(buildError(e));
        }
    }

    /**
     * Writes command to a switch.
     */
    protected CompletableFuture<Optional<OFMessage>> writeCommands(IOFSwitch sw, SessionService sessionService,
                                                                   FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        CompletableFuture<Optional<OFMessage>> chain = CompletableFuture.completedFuture(null);
        for (MessageWriter message : getCommands(sw, moduleContext)) {
            chain = chain.thenCompose(res -> {
                try {
                    return message.writeTo(sw, sessionService);
                } catch (SwitchWriteException e) {
                    throw new CompletionException(e);
                }
            });
        }
        return chain;
    }

    protected abstract FloodlightResponse buildError(Throwable error);

    protected FloodlightResponse buildResponse() {
        throw new IllegalStateException("No response received from the switch while processing command");
    }

    protected FloodlightResponse buildResponse(OFMessage response) {
        throw new IllegalStateException("Received unexpected message from switch while processing command");
    }

    public abstract List<MessageWriter> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException;

    protected final Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

}
