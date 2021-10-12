/* Copyright 2021 Telstra Open Source
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
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.command.flow.FlowSegmentCommand;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowModFactory;
import org.openkilda.floodlight.command.meter.MeterInstallCommand;
import org.openkilda.floodlight.command.meter.MeterInstallDryRunCommand;
import org.openkilda.floodlight.command.meter.MeterInstallReport;
import org.openkilda.floodlight.command.meter.MeterRemoveCommand;
import org.openkilda.floodlight.command.meter.MeterRemoveReport;
import org.openkilda.floodlight.command.meter.MeterVerifyCommand;
import org.openkilda.floodlight.command.meter.MeterVerifyReport;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.model.EffectiveIds;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.FlowTransitData;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.MeterSchema;

import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public abstract class IngressFlowSegmentBase extends FlowSegmentCommand {
    // payload
    protected final FlowEndpoint endpoint;
    protected final MeterConfig meterConfig;
    protected final SwitchId egressSwitchId;
    protected final RulesContext rulesContext;
    protected final FlowTransitEncapsulation encapsulation;

    // operation data
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    private IngressFlowModFactory flowModFactory;

    IngressFlowSegmentBase(
            MessageContext messageContext, SwitchId switchId, UUID commandId, FlowSegmentMetadata metadata,
            @NonNull FlowEndpoint endpoint, MeterConfig meterConfig, @NonNull SwitchId egressSwitchId,
            FlowTransitEncapsulation encapsulation, RulesContext rulesContext, MirrorConfig mirrorConfig) {
        super(messageContext, switchId, commandId, metadata, mirrorConfig);
        this.endpoint = endpoint;
        this.meterConfig = meterConfig;
        this.egressSwitchId = egressSwitchId;
        this.encapsulation = encapsulation;
        this.rulesContext = rulesContext;
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws Exception {
        super.setup(moduleContext);

        ensureSwitchEnoughCapabilities();

        setupFlowModFactory();
    }

    protected abstract void setupFlowModFactory();

    protected CompletableFuture<FlowSegmentReport> makeInstallPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<MeterId> meterIdFuture = CompletableFuture.completedFuture(null);
        if (meterConfig != null && rulesContext.isUpdateMeter()) {
            meterIdFuture = planMeterInstall(commandProcessor)
                    .thenApply(this::handleMeterReport);
        }

        CompletableFuture<GroupId> groupIdFuture = CompletableFuture.completedFuture(null);
        if (mirrorConfig != null) {
            if (mirrorConfig.isAddNewGroup()) {
                groupIdFuture = planGroupInstall(commandProcessor, getFlowTransitData());
            } else {
                groupIdFuture = planGroupModify(commandProcessor, getFlowTransitData());
            }
        }

        return meterIdFuture
                .thenCombine(groupIdFuture, EffectiveIds::new)
                .thenCompose(this::planOfFlowsInstall);
    }

    protected CompletableFuture<FlowSegmentReport> makeRemovePlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<MeterId> meterIdFuture = CompletableFuture.completedFuture(null);
        if (meterConfig != null && rulesContext.isUpdateMeter()) {
            meterIdFuture = planMeterDryRun(commandProcessor)
                    .thenApply(this::handleMeterReport);
        }

        CompletableFuture<GroupId> groupIdFuture = CompletableFuture.completedFuture(null);
        if (mirrorConfig != null) {
            groupIdFuture = planGroupDryRun(commandProcessor)
                    .thenApply(this::handleGroupReport);
        }

        CompletableFuture<EffectiveIds> future = meterIdFuture
                .thenCombine(groupIdFuture, EffectiveIds::new)
                .thenCompose(this::planOfFlowsRemove);

        CompletableFuture<Void> meterRemoveFuture = future
                .thenCompose(effectiveIds -> planMeterRemove(commandProcessor, effectiveIds.getMeterId()));

        CompletableFuture<Void> groupRemoveFuture = future
                .thenCompose(effectiveIds -> planGroupRemove(commandProcessor, effectiveIds.getGroupId()));

        return CompletableFuture.allOf(meterRemoveFuture, groupRemoveFuture)
                .thenApply(ignore -> makeSuccessReport());
    }

    protected CompletableFuture<FlowSegmentReport> makeVerifyPlan(SpeakerCommandProcessor commandProcessor) {
        CompletableFuture<MeterId> meterIdFuture = CompletableFuture.completedFuture(null);
        if (meterConfig != null) {
            meterIdFuture = planMeterVerify(commandProcessor)
                    .thenApply(this::handleMeterReport);
        }

        CompletableFuture<GroupId> groupIdFuture = CompletableFuture.completedFuture(null);
        if (mirrorConfig != null) {
            groupIdFuture = planGroupVerify(commandProcessor, getFlowTransitData())
                    .thenApply(this::handleGroupReport);
        }

        return meterIdFuture
                .thenCombine(groupIdFuture, EffectiveIds::new)
                .thenCompose(this::planOfFlowsVerify);
    }

    private CompletableFuture<MeterInstallReport> planMeterInstall(SpeakerCommandProcessor commandProcessor) {
        MeterInstallCommand meterCommand = new MeterInstallCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(meterCommand);
    }

    private CompletableFuture<Void> planMeterRemove(
            SpeakerCommandProcessor commandProcessor, MeterId effectiveMeterId) {
        if (effectiveMeterId == null || !getRulesContext().isUpdateMeter()) {
            if (meterConfig != null) {
                log.info(
                        "Do not remove meter {} on {} - switch do not support meters (i.e. it was not installed "
                                + "during flow segment install stage",
                        meterConfig, switchId);
            }
            return CompletableFuture.completedFuture(null);
        }

        MeterRemoveCommand removeCommand = new MeterRemoveCommand(messageContext, switchId, meterConfig.getId());
        return commandProcessor.chain(removeCommand)
                .thenAccept(this::handleMeterRemoveReport);
    }

    private CompletableFuture<MeterVerifyReport> planMeterVerify(SpeakerCommandProcessor commandProcessor) {
        MeterVerifyCommand meterVerify = new MeterVerifyCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(meterVerify);
    }

    private CompletableFuture<MeterInstallReport> planMeterDryRun(SpeakerCommandProcessor commandProcessor) {
        MeterInstallDryRunCommand meterDryRun = new MeterInstallDryRunCommand(messageContext, switchId, meterConfig);
        return commandProcessor.chain(meterDryRun);
    }

    private CompletableFuture<FlowSegmentReport> planOfFlowsInstall(EffectiveIds effectiveIds) {
        MeterId effectiveMeterId = effectiveIds.getMeterId();
        MeterConfig meterConfig = getMeterConfig();
        if (effectiveMeterId == null && rulesContext != null && !rulesContext.isUpdateMeter() && meterConfig != null) {
            effectiveIds.setMeterId(meterConfig.getId());
        }

        List<OFFlowMod> ofMessages = makeFlowModMessages(effectiveIds);
        List<CompletableFuture<Optional<OFMessage>>> writeResults = new ArrayList<>(ofMessages.size());
        try (Session session = getSessionService().open(messageContext, getSw())) {
            for (OFFlowMod message : ofMessages) {
                writeResults.add(session.write(message));
            }
        }
        return CompletableFuture.allOf(writeResults.toArray(new CompletableFuture[0]))
                .thenApply(ignore -> makeSuccessReport());
    }

    private CompletableFuture<EffectiveIds> planOfFlowsRemove(EffectiveIds effectiveIds) {
        List<OFFlowMod> ofMessages = new ArrayList<>(makeFlowModMessages(effectiveIds));

        List<CompletableFuture<?>> requests = new ArrayList<>(ofMessages.size());
        try (Session session = getSessionService().open(messageContext, getSw())) {
            for (OFFlowMod message : ofMessages) {
                requests.add(session.write(message));
            }
        }

        return CompletableFuture.allOf(requests.toArray(new CompletableFuture<?>[0]))
                .thenApply(ignore -> effectiveIds);
    }

    private CompletableFuture<FlowSegmentReport> planOfFlowsVerify(EffectiveIds effectiveIds) {
        return makeVerifyPlan(makeFlowModMessages(effectiveIds));
    }

    private MeterId handleMeterReport(MeterInstallReport report) {
        ensureMeterSuccess(report);
        return report.getMeterId()
                .orElse(null);
    }

    private MeterId handleMeterReport(MeterVerifyReport report) {
        ensureMeterSuccess(report);
        return report.getSchema()
                .map(MeterSchema::getMeterId)
                .orElse(null);
    }

    private void handleMeterRemoveReport(MeterRemoveReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.info("Do not remove meter id {} from {} - {}", meterConfig.getId(), switchId, e.getMessage());
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
    }

    protected List<OFFlowMod> makeFlowModMessages(EffectiveIds effectiveIds) {
        if (metadata.isMultiTable()) {
            return makeMultiTableFlowModMessages(effectiveIds);
        } else {
            return makeSingleTableFlowModMessages(effectiveIds);
        }
    }

    protected List<OFFlowMod> makeMultiTableFlowModMessages(EffectiveIds effectiveIds) {
        List<OFFlowMod> ofMessages = new ArrayList<>(2);
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            if (FlowEndpoint.isVlanIdSet(endpoint.getInnerVlanId())) {
                ofMessages.add(flowModFactory.makeDoubleVlanForwardMessage(effectiveIds));
            } else {
                ofMessages.add(flowModFactory.makeSingleVlanForwardMessage(effectiveIds));
            }
        } else {
            ofMessages.add(flowModFactory.makeDefaultPortForwardMessage(effectiveIds));
        }

        return ofMessages;
    }

    protected List<OFFlowMod> makeSingleTableFlowModMessages(EffectiveIds effectiveIds) {
        List<OFFlowMod> ofMessages = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            ofMessages.add(flowModFactory.makeOuterOnlyVlanForwardMessage(effectiveIds));
        } else {
            ofMessages.add(flowModFactory.makeDefaultPortForwardMessage(effectiveIds));
        }
        return ofMessages;
    }

    /**
     * Make flow-mod-requests to install shared OF flow. Shared OF flows can be used from 1 up to many kilda-flows. They
     * are required to route network packets into the correct OF table or to make a complex packet match that can't be
     * done in a single OF table (QinQ case).
     *
     * <p>Because there shared rules for all kilda-flows use the same priority and match fields, it is safe to install
     * them for each kilda-flows they are used in. If there is no such OF flow on the switch - it will be installed, if
     * it already there - it will replace existing (there will be no change in any meaning OF flow field).
     */
    protected List<OFFlowMod> makeSharedFlowModInstallMessages() {
        List<OFFlowMod> ofMessages = new ArrayList<>();
        if (metadata.isMultiTable()) {
            ofMessages.add(getFlowModFactory().makeCustomerPortSharedCatchMessage());

            if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
                ofMessages.add(flowModFactory.makeOuterVlanMatchSharedMessage());

                if (rulesContext != null && rulesContext.isInstallServer42OuterVlanMatchSharedRule()) {
                    ofMessages.add(flowModFactory.makeServer42OuterVlanMatchSharedMessage());
                }
            }

            if (getEndpoint().isTrackLldpConnectedDevices()) {
                ofMessages.add(getFlowModFactory().makeLldpInputCustomerFlowMessage());
            }

            if (getEndpoint().isTrackArpConnectedDevices()) {
                ofMessages.add(getFlowModFactory().makeArpInputCustomerFlowMessage());
            }
        }
        return ofMessages;
    }

    /**
     * Make flow-mod-requests to remove shared OF flow. The Caller must define which shared OF flows must be removed.
     * I.e. shared OF flow can be removed only when there is no more kilda-flows uses it.
     */
    protected List<OFFlowMod> makeSharedFlowModRemoveMessages() {
        List<OFFlowMod> ofMessages = new ArrayList<>();
        if (getSwitchFeatures().contains(SwitchFeature.MULTI_TABLE) && rulesContext != null) {
            if (rulesContext.isRemoveCustomerCatchRule()) {
                ofMessages.add(getFlowModFactory().makeCustomerPortSharedCatchMessage());
            }
            if (rulesContext.isRemoveCustomerLldpRule()) {
                ofMessages.add(getFlowModFactory().makeLldpInputCustomerFlowMessage());
            }
            if (rulesContext.isRemoveCustomerArpRule()) {
                ofMessages.add(getFlowModFactory().makeArpInputCustomerFlowMessage());
            }
            if (rulesContext.isRemoveOuterVlanMatchSharedRule()) {
                ofMessages.add(getFlowModFactory().makeOuterVlanMatchSharedMessage());
            }
            if (rulesContext.isRemoveServer42OuterVlanMatchSharedRule()) {
                ofMessages.add(getFlowModFactory().makeServer42OuterVlanMatchSharedMessage());
            }
        }
        return ofMessages;
    }

    protected List<OFFlowMod> makeServer42IngressFlowModMessages() {
        if (metadata.isMultiTable()) {
            return makeMultiTableServer42IngressFlowModMessages();
        } else {
            return makeSingleTableServer42IngressFlowModMessages();
        }
    }

    protected List<OFFlowMod> makeMultiTableServer42IngressFlowModMessages() {
        List<OFFlowMod> ofMessages = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            if (FlowEndpoint.isVlanIdSet(endpoint.getInnerVlanId())) {
                ofMessages.add(flowModFactory.makeDoubleVlanServer42IngressFlowMessage());
            } else {
                ofMessages.add(flowModFactory.makeSingleVlanServer42IngressFlowMessage());
            }
        } else {
            ofMessages.add(flowModFactory.makeDefaultPortServer42IngressFlowMessage(
                    getKildaCoreConfig().getServer42FlowRttUdpPortOffset()));
        }

        return ofMessages;
    }

    protected List<OFFlowMod> makeSingleTableServer42IngressFlowModMessages() {
        List<OFFlowMod> ofMessages = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            ofMessages.add(flowModFactory.makeOuterOnlyVlanServer42IngressFlowMessage(
                    getKildaCoreConfig().getServer42FlowRttUdpPortOffset()));
        } else {
            ofMessages.add(flowModFactory.makeDefaultPortServer42IngressFlowMessage(
                    getKildaCoreConfig().getServer42FlowRttUdpPortOffset()));
        }
        return ofMessages;
    }

    protected List<OFFlowMod> makeIngressLoopFlowModMessages() {
        if (metadata.isMultiTable()) {
            return makeMultiTableLoopFlowModMessages();
        } else {
            return makeSingleTableLoopFlowModMessages();
        }
    }

    protected List<OFFlowMod> makeMultiTableLoopFlowModMessages() {
        List<OFFlowMod> ofMessages = new ArrayList<>(2);
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            if (FlowEndpoint.isVlanIdSet(endpoint.getInnerVlanId())) {
                ofMessages.add(flowModFactory.makeDoubleVlanFlowLoopMessage());
            } else {
                ofMessages.add(flowModFactory.makeSingleVlanFlowLoopMessage());
            }
        } else {
            ofMessages.add(flowModFactory.makeDefaultPortIngressFlowLoopMessage());
        }

        return ofMessages;
    }

    protected List<OFFlowMod> makeSingleTableLoopFlowModMessages() {
        List<OFFlowMod> ofMessages = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())) {
            ofMessages.add(flowModFactory.makeOuterOnlyVlanIngressFlowLoopMessage());
        } else {
            ofMessages.add(flowModFactory.makeDefaultPortIngressFlowLoopMessage());
        }
        return ofMessages;
    }

    /**
     * Returns a list of sets of required features.
     * Switch must support at least one feature from each set.
     */
    protected List<Set<SwitchFeature>> getRequiredFeatures() {
        return new ArrayList<>();
    }

    protected void ensureMeterSuccess(SpeakerCommandReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.info(
                    "Meter id {} on {} ignored by command {} - - {}",
                    meterConfig.getId(), switchId, getClass().getCanonicalName(), e.getMessage());
            // switch do not support meters, setup rules without meter
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
    }

    private void ensureSwitchEnoughCapabilities() throws UnsupportedSwitchOperationException {
        List<Set<SwitchFeature>> required = getRequiredFeatures();
        Set<SwitchFeature> switchFeatures = getSwitchFeatures();

        List<Set<SwitchFeature>> missedFeatures = new ArrayList<>();
        for (Set<SwitchFeature> requiredSet : required) {
            if (Sets.intersection(requiredSet, switchFeatures).isEmpty()) {
                missedFeatures.add(requiredSet);
            }
        }
        if (missedFeatures.isEmpty()) {
            return;
        }

        throw new UnsupportedSwitchOperationException(
                getSw().getId(), String.format("Switch %s must support at least one feature from each of the following "
                + "lists: %s", switchId, missedFeatures));
    }

    private FlowTransitData getFlowTransitData() {
        return Optional.ofNullable(encapsulation)
                .map(transitEncapsulation -> FlowTransitData.builder()
                        .encapsulation(transitEncapsulation)
                        .ingressSwitchId(switchId)
                        .egressSwitchId(egressSwitchId)
                        .build())
                .orElse(null);
    }
}
