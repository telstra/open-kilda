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

package org.openkilda.floodlight.command.meter;

import static org.openkilda.model.MeterId.MAX_FLOW_METER_ID;
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID;

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModFailedCode;
import org.projectfloodlight.openflow.protocol.errormsg.OFMeterModFailedErrorMsg;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Getter
public class MeterModifyCommand extends MeterInstallCommand {

    private SpeakerCommandProcessor commandProcessor;

    public MeterModifyCommand(MessageContext messageContext, SwitchId switchId, MeterConfig meterConfig) {
        super(messageContext, switchId, meterConfig);
    }

    @Override
    protected CompletableFuture<MeterInstallReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor)
            throws UnsupportedSwitchOperationException, InvalidMeterIdException {
        this.commandProcessor = commandProcessor;

        ensureSwitchSupportMeters();
        ensureMeterIdValid();

        OFMeterMod meterMod = makeMeterModifyMessage();
        return writeSwitchRequest(meterMod)
                .thenApply(ignore -> makeSuccessReport());
    }

    @Override
    public CompletableFuture<Optional<OFMessage>> handleOfError(OFErrorMsg response) {
        CompletableFuture<Optional<OFMessage>> future = new CompletableFuture<>();
        String errorMessage;
        if (isUnknownMeter(response)) {
            errorMessage = String.format("Can't modify non existent meter. sw:%s meter:%s",
                    getSw().getId(), meterConfig.getId());
        } else {
            errorMessage = String.format("Can't modify meter %s on switch %s - %s",
                    meterConfig.getId(), getSw().getId(), response);
        }
        future.completeExceptionally(new SwitchErrorResponseException(getSw().getId(), errorMessage));
        return future;
    }

    private boolean isUnknownMeter(OFErrorMsg response) {
        if (!(response instanceof OFMeterModFailedErrorMsg)) {
            return false;
        }
        return ((OFMeterModFailedErrorMsg) response).getCode() == OFMeterModFailedCode.UNKNOWN_METER;
    }

    @Override
    protected void ensureMeterIdValid() throws InvalidMeterIdException {
        MeterId meterId = meterConfig.getId();
        if (meterId == null || meterId.getValue() < MIN_FLOW_METER_ID || meterId.getValue() > MAX_FLOW_METER_ID) {
            throw new InvalidMeterIdException(getSw().getId(), String.format(
                    "Invalid flow meterId value - expect meter id in range [%s, %s], got - %s",
                    MIN_FLOW_METER_ID, MAX_FLOW_METER_ID, meterId));
        }
    }
}
