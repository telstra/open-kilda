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

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.command.group.GroupInstallCommand;
import org.openkilda.floodlight.command.group.GroupInstallDryRunCommand;
import org.openkilda.floodlight.command.group.GroupInstallReport;
import org.openkilda.floodlight.command.group.GroupModifyCommand;
import org.openkilda.floodlight.command.group.GroupRemoveCommand;
import org.openkilda.floodlight.command.group.GroupRemoveReport;
import org.openkilda.floodlight.command.group.GroupVerifyCommand;
import org.openkilda.floodlight.command.group.GroupVerifyReport;
import org.openkilda.floodlight.error.SwitchMissingFlowsException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.utils.OfFlowDumpProducer;
import org.openkilda.floodlight.utils.OfFlowPresenceVerifier;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFlowMod;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public abstract class FlowSegmentCommand extends SpeakerCommand<FlowSegmentReport> {
    public static final int FLOW_PRIORITY = FlowModUtils.PRIORITY_HIGH;

    // payload
    protected final FlowSegmentMetadata metadata;
    protected final MirrorConfig mirrorConfig;

    // operation data
    @Getter(AccessLevel.PROTECTED)
    private Set<SwitchFeature> switchFeatures;

    @Getter(AccessLevel.PROTECTED)
    private KildaCoreConfig kildaCoreConfig;

    public FlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, @NonNull FlowSegmentMetadata metadata,
            MirrorConfig mirrorConfig) {
        super(messageContext, switchId, commandId);

        this.metadata = metadata;
        this.mirrorConfig = mirrorConfig;
    }

    @Override
    protected void setup(FloodlightModuleContext moduleContext) throws Exception {
        super.setup(moduleContext);

        FeatureDetectorService featureDetectorService = moduleContext.getServiceImpl(FeatureDetectorService.class);
        switchFeatures = featureDetectorService.detectSwitch(getSw());

        KildaCore kildaCore = moduleContext.getServiceImpl(KildaCore.class);
        kildaCoreConfig = kildaCore.getConfig();
    }

    protected CompletableFuture<FlowSegmentReport> makeVerifyPlan(List<OFFlowMod> expected) {
        OfFlowDumpProducer dumper = new OfFlowDumpProducer(messageContext, getSw(), expected);
        OfFlowPresenceVerifier verifier = new OfFlowPresenceVerifier(dumper, expected, switchFeatures);
        return verifier.getFinish()
                .thenApply(verifyResults -> handleVerifyResponse(expected, verifyResults));
    }

    protected FlowSegmentReport makeReport(Exception error) {
        return new FlowSegmentReport(this, error);
    }

    protected FlowSegmentReport makeSuccessReport() {
        return new FlowSegmentReport(this);
    }

    private FlowSegmentReport handleVerifyResponse(List<OFFlowMod> expected, OfFlowPresenceVerifier verifier) {
        List<OFFlowMod> missing = verifier.getMissing();
        if (missing.isEmpty()) {
            return makeSuccessReport();
        }

        return new FlowSegmentReport(
                this, new SwitchMissingFlowsException(
                        getSw().getId(), metadata, expected, missing));
    }

    protected CompletableFuture<GroupId> planGroupInstall(SpeakerCommandProcessor commandProcessor) {
        GroupInstallCommand groupCommand = new GroupInstallCommand(messageContext, switchId, mirrorConfig);

        return commandProcessor.chain(groupCommand)
                .thenApply(this::handleGroupReport);
    }

    protected CompletableFuture<GroupId> planGroupModify(SpeakerCommandProcessor commandProcessor) {
        GroupInstallCommand groupCommand = new GroupModifyCommand(messageContext, switchId, mirrorConfig);

        return commandProcessor.chain(groupCommand)
                .thenApply(this::handleGroupReport);
    }

    protected CompletableFuture<Void> planGroupRemove(
            SpeakerCommandProcessor commandProcessor, GroupId effectiveGroupId) {
        if (effectiveGroupId == null) {
            if (mirrorConfig != null) {
                log.info(
                        "Do not remove group {} on {} - switch does not support groups (i.e. it was not installed "
                                + "during flow segment install stage",
                        mirrorConfig, switchId);
            }
            return CompletableFuture.completedFuture(null);
        }

        GroupRemoveCommand removeCommand = new GroupRemoveCommand(messageContext, switchId, mirrorConfig.getGroupId());
        return commandProcessor.chain(removeCommand)
                .thenAccept(this::handleGroupRemoveReport);
    }

    protected CompletableFuture<GroupInstallReport> planGroupDryRun(SpeakerCommandProcessor commandProcessor) {
        GroupInstallDryRunCommand groupDryRun = new GroupInstallDryRunCommand(messageContext, switchId, mirrorConfig);
        return commandProcessor.chain(groupDryRun);
    }

    protected CompletableFuture<GroupVerifyReport> planGroupVerify(SpeakerCommandProcessor commandProcessor) {
        GroupVerifyCommand groupVerify = new GroupVerifyCommand(messageContext, switchId, mirrorConfig);
        return commandProcessor.chain(groupVerify);
    }

    protected GroupId handleGroupReport(GroupInstallReport report) {
        ensureGroupSuccess(report);
        return report.getGroupId()
                .orElse(null);
    }

    protected GroupId handleGroupReport(GroupVerifyReport report) {
        ensureGroupSuccess(report);
        return report.getMirrorConfig().get().getGroupId();
    }

    protected void handleGroupRemoveReport(GroupRemoveReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.info("Do not remove group id {} from {} - {}", mirrorConfig.getGroupId(), switchId, e.getMessage());
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
    }

    protected void ensureGroupSuccess(SpeakerCommandReport report) {
        try {
            report.raiseError();
        } catch (UnsupportedSwitchOperationException e) {
            log.info(
                    "Group id {} on {} ignored by command {} - - {}",
                    mirrorConfig.getGroupId(), switchId, getClass().getCanonicalName(), e.getMessage());
            // switch do not support groups, setup rules without meter
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
    }

    public Cookie getCookie() {
        return metadata.getCookie();
    }

    protected enum SegmentAction {
        INSTALL, REMOVE, VERIFY
    }
}
