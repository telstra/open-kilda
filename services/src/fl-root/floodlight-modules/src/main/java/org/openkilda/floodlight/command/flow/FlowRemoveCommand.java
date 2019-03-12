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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.MessageWriter;
import org.openkilda.floodlight.command.OfCommand;
import org.openkilda.floodlight.command.meter.RemoveMeterCommand;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlowRemoveCommand extends FlowCommand {

    private final Long meterId;
    private final DeleteRulesCriteria criteria;

    public FlowRemoveCommand(@JsonProperty("command_id") String commandId,
                             @JsonProperty("flowid") String flowId,
                             @JsonProperty("message_context") MessageContext messageContext,
                             @JsonProperty("cookie") Long cookie,
                             @JsonProperty("switch_id") SwitchId switchId,
                             @JsonProperty("meter_id") Long meterId,
                             @JsonProperty("criteria") DeleteRulesCriteria criteria) {
        super(commandId, flowId, messageContext, cookie, switchId);
        this.meterId = meterId;
        this.criteria = criteria;
    }

    @Override
    protected FloodlightResponse buildResponse(OFMessage response) {
        return FlowResponse.builder()
                .commandId(commandId)
                .flowId(flowId)
                .messageContext(messageContext)
                .success(true)
                .switchId(switchId)
                .build();
    }

    @Override
    protected CompletableFuture<Optional<OFMessage>> writeCommand(IOFSwitch sw, SessionService sessionService,
                                                                  FloodlightModuleContext moduleContext) {
        CompletableFuture<List<OFFlowStatsEntry>> entriesBeforeDeletion = dumpFlowTable(sw);

        CompletableFuture<List<OFFlowStatsEntry>> entriesAfterDeletion = entriesBeforeDeletion
                .thenCompose(entries ->  {
                    try {
                        return super.writeCommand(sw, sessionService, moduleContext);
                    } catch (SwitchOperationException e) {
                        throw new CompletionException(e);
                    }})
                .thenCompose(deleted -> dumpFlowTable(sw));

        return entriesAfterDeletion.thenCombine(entriesBeforeDeletion, (entriesAfter, entriesBefore) ->
                entriesBefore.stream()
                        .map(entry -> entry.getCookie().getValue())
                        .filter(cookieBefore -> entriesAfter.stream()
                                .noneMatch(after -> after.getCookie().getValue() == cookieBefore))
                        .peek(cookie -> getLogger().info("Rule with cookie {} has been removed from switch {}.",
                                cookie, sw.getId())))
                .thenApply(ignored -> Optional.empty());
    }

    @Override
    public List<MessageWriter> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext) {
        List<MessageWriter> commands = new ArrayList<>();

        try {
            OfCommand meterRemoveCommand = new RemoveMeterCommand(messageContext, switchId, meterId);
            commands.addAll(meterRemoveCommand.getCommands(sw, moduleContext));
        } catch (UnsupportedSwitchOperationException e) {
            getLogger().debug("Meter {} won't be deleted from switch {}: {}", meterId, e.getDpId(), e.getMessage());
        } catch (SwitchOperationException e) {
            buildError(e);
        }

        getDeleteCommands(sw, criteria)
                .stream()
                .map(MessageWriter::new)
                .forEach(commands::add);

        return commands;
    }

    List<OFFlowDelete> getDeleteCommands(IOFSwitch sw, DeleteRulesCriteria... criterias) {
        return Stream.of(criterias)
                .peek(criteria -> getLogger().info("Rules by criteria {} are to be removed from switch {}.",
                        criteria, sw.getId()))
                .map(criteria -> buildFlowDeleteByCriteria(sw.getOFFactory(), criteria))
                .collect(Collectors.toList());
    }

    private OFFlowDelete buildFlowDeleteByCriteria(OFFactory ofFactory, DeleteRulesCriteria criteria) {
        OFFlowDelete.Builder builder = ofFactory.buildFlowDelete();
        Optional.ofNullable(criteria.getCookie())
                .ifPresent(flowCookie -> {
                    builder.setCookie(U64.of(criteria.getCookie()));
                    builder.setCookieMask(U64.NO_MASK);
                });

        if (criteria.getInPort() != null) {
            // Match either In Port or both Port & Vlan criteria.
            Match match = matchFlow(criteria.getInPort(),
                    Optional.ofNullable(criteria.getInVlan()).orElse(0), ofFactory);
            builder.setMatch(match);
        } else if (criteria.getInVlan() != null) {
            // Match In Vlan criterion if In Port is not specified
            Match.Builder matchBuilder = ofFactory.buildMatch();
            matchVlan(ofFactory, matchBuilder, criteria.getInVlan());
            builder.setMatch(matchBuilder.build());
        }

        Optional.ofNullable(criteria.getPriority())
                .ifPresent(priority -> builder.setPriority(criteria.getPriority()));

        Optional.ofNullable(criteria.getOutPort())
                .ifPresent(outPort -> builder.setOutPort(OFPort.of(criteria.getOutPort())));

        return builder.build();
    }
}
