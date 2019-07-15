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

package org.openkilda.floodlight.command.flow.ingress;

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.FlowSegmentCommand;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.meter.MeterInstallCommand;
import org.openkilda.floodlight.command.meter.MeterRemoveCommand;
import org.openkilda.floodlight.command.meter.MeterReport;
import org.openkilda.floodlight.command.meter.MeterVerifyCommand;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Getter
abstract class IngressFlowSegmentCommand extends FlowSegmentCommand {
    // payload
    protected final FlowEndpoint endpoint;
    protected final MeterConfig meterConfig;

    // operation data
    @Getter(AccessLevel.PROTECTED)
    private Set<SwitchFeature> switchFeatures;

    IngressFlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, FlowSegmentMetadata metadata,
            @NonNull FlowEndpoint endpoint, MeterConfig meterConfig) {
        super(messageContext, switchId, commandId, metadata);
        this.endpoint = endpoint;
        this.meterConfig = meterConfig;
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws Exception {
        super.setup(moduleContext);

        FeatureDetectorService featureDetectorService = moduleContext.getServiceImpl(FeatureDetectorService.class);
        switchFeatures = featureDetectorService.detectSwitch(getSw());

        ensureSwitchEnoughCapabilities();
    }

    protected CompletableFuture<FlowSegmentReport> makeInstallPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<MeterId> future = CompletableFuture.completedFuture(null);
        if (meterConfig != null) {
            future = planMeterInstall(commandProcessor)
                    .thenApply(this::handleMeterReport)
                    .thenApply(MeterReport::getMeterId);
        }
        return future.thenCompose(this::planOfFlowsInstall);
    }

    protected CompletableFuture<FlowSegmentReport> makeRemovePlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<Void> future = planOfFlowsRemove();
        if (meterConfig != null) {
            future = future.thenCompose(ignore -> planMeterRemove(commandProcessor));
        }
        return future.thenApply(ignore -> makeSuccessReport());
    }

    protected CompletableFuture<FlowSegmentReport> makeVerifyPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<MeterId> future = CompletableFuture.completedFuture(null);
        if (meterConfig != null) {
            future = planMeterVerify(commandProcessor)
                    .thenApply(this::handleMeterReport)
                    .thenApply(MeterReport::getMeterId);
        }
        return future.thenCompose(this::planOfFlowsVerify);
    }

    private CompletableFuture<MeterReport> planMeterInstall(SpeakerCommandProcessor commandProcessor) {
        MeterInstallCommand meterCommand = new MeterInstallCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(meterCommand);
    }

    private CompletableFuture<Void> planMeterRemove(SpeakerCommandProcessor commandProcessor) {
        MeterRemoveCommand removeCommand = new MeterRemoveCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(removeCommand)
                .thenAccept(this::handleMeterRemoveReport);
    }

    private CompletableFuture<MeterReport> planMeterVerify(SpeakerCommandProcessor commandProcessor) {
        MeterVerifyCommand meterVerify = new MeterVerifyCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(meterVerify);
    }

    protected MeterReport handleMeterReport(MeterReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.info("Do not install meter id {} on {} - {}", meterConfig.getId(), switchId, e.getMessage());
            // switch do not support meters, setup rules without meter
        } catch (Exception e) {
            throw maskCallbackException(e);
        }

        return report;
    }

    private CompletableFuture<FlowSegmentReport> planOfFlowsInstall(MeterId effectiveMeterId) {
        List<OFFlowMod> ofMessages = makeIngressModMessages(effectiveMeterId);
        List<CompletableFuture<Optional<OFMessage>>> writeResults = new ArrayList<>(ofMessages.size());
        try (Session session = getSessionService().open(messageContext, getSw())) {
            for (OFFlowMod message : ofMessages) {
                writeResults.add(session.write(message));
            }
        }
        return CompletableFuture.allOf(writeResults.toArray(new CompletableFuture[0]))
                .thenApply(ignore -> makeSuccessReport());
    }

    private CompletableFuture<Void> planOfFlowsRemove() {
        MeterId meterId = null;
        if (meterConfig != null) {
            meterId = meterConfig.getId();
        }
        List<OFFlowMod> ofMessages = new ArrayList<>(makeIngressModMessages(meterId));

        List<CompletableFuture<?>> requests = new ArrayList<>(ofMessages.size());
        try (Session session = getSessionService().open(messageContext, getSw())) {
            for (OFFlowMod message : ofMessages) {
                requests.add(session.write(message));
            }
        }

        return CompletableFuture.allOf(requests.toArray(new CompletableFuture<?>[0]));
    }

    private CompletableFuture<FlowSegmentReport> planOfFlowsVerify(MeterId effectiveMeterId) {
        return makeVerifyPlan(makeIngressModMessages(effectiveMeterId));
    }

    private void handleMeterRemoveReport(MeterReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.info("Do not remove meter id {} from {} - {}", meterConfig.getId(), switchId, e.getMessage());
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
    }

    protected List<OFFlowMod> makeIngressModMessages(MeterId effectiveMeterId) {
        List<OFFlowMod> ofMessages = new ArrayList<>();
        OFFactory of = getSw().getOFFactory();
        if (FlowEndpoint.isVlanIdSet(endpoint.getVlanId())) {
            ofMessages.add(makeOuterVlanOnlyForwardMessage(of, effectiveMeterId));
        } else {
            ofMessages.add(makeDefaultPortFlowMatchAndForwardMessage(of, effectiveMeterId));
        }

        return ofMessages;
    }

    private OFFlowMod makeOuterVlanOnlyForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        OFFlowMod.Builder builder = makeFlowModBuilder(of)
                .setPriority(FLOW_PRIORITY)
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), getEndpoint().getVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                                  .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeDefaultPortFlowMatchAndForwardMessage(OFFactory of, MeterId effectiveMeterId) {
        OFFlowMod.Builder builder = makeFlowModBuilder(of)
                // FIXME we need some space between match rules (so it should be -10 instead of -1)
                .setPriority(FLOW_PRIORITY - 1)
                .setMatch(of.buildMatch()
                        .setExact(MatchField.IN_PORT, OFPort.of(endpoint.getPortNumber()))
                        .build());
        return makeForwardMessage(of, builder, effectiveMeterId);
    }

    private OFFlowMod makeForwardMessage(OFFactory of, OFFlowMod.Builder builder, MeterId effectiveMeterId) {
        builder.setInstructions(makeForwardMessageInstructions(of, effectiveMeterId));
        if (getSwitchFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.setFlags(ImmutableSet.of(OFFlowModFlags.RESET_COUNTS));
        }
        return builder.build();
    }

    protected List<OFInstruction> makeForwardMessageInstructions(OFFactory of, MeterId effectiveMeterId) {
        List<OFAction> applyActions = new ArrayList<>();
        List<OFInstruction> instructions = new ArrayList<>();

        if (effectiveMeterId != null) {
            OfAdapter.INSTANCE.makeMeterCall(of, effectiveMeterId, applyActions, instructions);
        }

        applyActions.addAll(makeTransformActions(of));
        applyActions.add(makeOutputAction(of));

        instructions.add(of.instructions().applyActions(applyActions));
        return instructions;
    }

    protected abstract List<OFAction> makeTransformActions(OFFactory of);

    protected abstract OFAction makeOutputAction(OFFactory of);

    protected final OFAction makeOutputAction(OFFactory of, OFPort port) {
        return of.actions().buildOutput()
                .setPort(port)
                .build();
    }

    protected Set<SwitchFeature> getRequiredFeatures() {
        return new HashSet<>();
    }

    private void ensureSwitchEnoughCapabilities() throws UnsupportedSwitchOperationException {
        Set<SwitchFeature> required = getRequiredFeatures();
        required.removeAll(switchFeatures);
        if (required.isEmpty()) {
            return;
        }

        String requiredAsString = required.stream()
                .map(SwitchFeature::name)
                .sorted()
                .collect(Collectors.joining(", "));
        throw new UnsupportedSwitchOperationException(
                getSw().getId(), String.format("Switch %s do not support %s", switchId, requiredAsString));
    }
}
