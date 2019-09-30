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

import static org.openkilda.floodlight.switchmanager.SwitchManager.convertDpIdToMac;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.MessageWriter;
import org.openkilda.floodlight.command.SessionProxy;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.meter.RemoveMeterCommand;
import org.openkilda.floodlight.error.OfDeleteException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.Cookie;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlowRemoveCommand extends FlowCommand {

    private final MeterId meterId;
    private final DeleteRulesCriteria criteria;

    public FlowRemoveCommand(@JsonProperty("command_id") UUID commandId,
                             @JsonProperty("flowid") String flowId,
                             @JsonProperty("message_context") MessageContext messageContext,
                             @JsonProperty("cookie") Cookie cookie,
                             @JsonProperty("switch_id") SwitchId switchId,
                             @JsonProperty("meter_id") MeterId meterId,
                             @JsonProperty("criteria") DeleteRulesCriteria criteria,
                             @JsonProperty("multi_table") boolean multiTable) {
        super(commandId, flowId, messageContext, cookie, switchId, multiTable);
        this.meterId = meterId;
        this.criteria = criteria;
    }

    @Override
    protected FloodlightResponse buildResponse() {
        return FlowResponse.builder()
                .commandId(commandId)
                .flowId(flowId)
                .messageContext(messageContext)
                .success(true)
                .switchId(switchId)
                .build();
    }

    @Override
    protected CompletableFuture<Optional<OFMessage>> writeCommands(IOFSwitch sw,
                                                                   FloodlightModuleContext moduleContext) {
        SessionService sessionService = moduleContext.getServiceImpl(SessionService.class);

        CompletableFuture<List<OFFlowStatsEntry>> entriesBeforeDeletion = dumpFlowTable(sw);
        CompletableFuture<List<OFFlowStatsEntry>> entriesAfterDeletion = entriesBeforeDeletion
                .thenCompose(ignored -> removeRules(sw, sessionService, moduleContext))
                .thenCompose(ignored -> dumpFlowTable(sw));

        return entriesAfterDeletion
                .thenCombine(entriesBeforeDeletion, (after, before) -> verifyRulesDeleted(sw.getId(), before, after));
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        List<SessionProxy> commands = new ArrayList<>();

        getDeleteMeterCommand(sw, moduleContext)
                .ifPresent(commands::add);

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

    private CompletableFuture<Optional<OFMessage>> removeRules(IOFSwitch sw, SessionService sessionService,
                                                               FloodlightModuleContext moduleContext) {
        try {
            return super.writeCommands(sw, moduleContext);
        } catch (SwitchOperationException e) {
            throw new CompletionException(e);
        }
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
            MacAddress dstMac = criteria.getEgressSwitchId() != null
                    ? convertDpIdToMac(DatapathId.of(criteria.getEgressSwitchId().toLong()))
                    : null;
            Match match = matchFlow(criteria.getInPort(),

                    Optional.ofNullable(criteria.getEncapsulationId()).orElse(0),
                    criteria.getEncapsulationType(), dstMac, ofFactory);
            builder.setMatch(match);
        } else if (criteria.getEncapsulationId() != null) {
            // Match In Vlan criterion if In Port is not specified
            Match.Builder matchBuilder = ofFactory.buildMatch();
            matchVlan(ofFactory, matchBuilder, criteria.getEncapsulationId());
            builder.setMatch(matchBuilder.build());
        }

        Optional.ofNullable(criteria.getPriority())
                .ifPresent(priority -> builder.setPriority(criteria.getPriority()));

        Optional.ofNullable(criteria.getOutPort())
                .ifPresent(outPort -> builder.setOutPort(OFPort.of(criteria.getOutPort())));

        return builder.build();
    }

    private Optional<SessionProxy> getDeleteMeterCommand(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        if (meterId == null) {
            return Optional.empty();
        }

        try {
            SpeakerCommand meterCommand = new RemoveMeterCommand(messageContext, switchId, meterId);
            return meterCommand.getCommands(sw, moduleContext).stream().findFirst();
        } catch (UnsupportedSwitchOperationException e) {
            getLogger().debug("Skip meter {} deletion for flow {} on switch {}: {}",
                    meterId, flowId, switchId.toString(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<OFMessage> verifyRulesDeleted(DatapathId dpid, List<OFFlowStatsEntry> entriesBefore,
                                                   List<OFFlowStatsEntry> entriesAfter) {
        entriesAfter.stream()
                .filter(entry -> entry.getCookie().equals(U64.of(cookie.getValue())))
                .findAny()
                .ifPresent(nonDeleted -> {
                    throw new CompletionException(new OfDeleteException(dpid, cookie.getValue()));
                });

        // we might accidentally delete rules belong to another flows. In order to be able to track it we write all
        // deleted rules into the log.
        entriesBefore.stream()
                .map(entry -> entry.getCookie().getValue())
                .filter(cookieBefore -> entriesAfter.stream()
                        .noneMatch(after -> after.getCookie().getValue() == cookieBefore))
                .forEach(deleted -> getLogger().info("Rule with cookie {} has been removed from switch {}.",
                        deleted, switchId));

        return Optional.empty();
    }
}
