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

import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.error.SwitchMissingFlowsException;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.utils.OfFlowDumpProducer;
import org.openkilda.floodlight.utils.OfFlowPresenceVerifier;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import lombok.NonNull;
import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFlowMod;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public abstract class FlowSegmentCommand extends SpeakerCommand<FlowSegmentReport> {
    public static final int FLOW_PRIORITY = FlowModUtils.PRIORITY_HIGH;

    // payload
    protected final FlowSegmentMetadata metadata;

    public FlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, @NonNull FlowSegmentMetadata metadata) {
        super(messageContext, switchId, commandId);

        this.metadata = metadata;
    }

    protected CompletableFuture<FlowSegmentReport> makeVerifyPlan(List<OFFlowMod> expected) {
        OfFlowDumpProducer dumper = new OfFlowDumpProducer(messageContext, getSw(), expected);
        OfFlowPresenceVerifier verifier = new OfFlowPresenceVerifier(dumper, expected);
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

    public Cookie getCookie() {
        return metadata.getCookie();
    }

    protected enum SegmentAction {
        INSTALL, REMOVE, VERIFY
    }
}
