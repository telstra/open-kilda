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

package org.openkilda.floodlight.command.meter;

import org.openkilda.floodlight.command.IOfErrorResponseHandler;
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchIncorrectMeterException;
import org.openkilda.floodlight.error.SwitchMeterConflictException;
import org.openkilda.floodlight.error.SwitchMissingMeterException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.session.Session;
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
public class MeterInstallCommand extends AbstractMeterInstall<MeterInstallReport> implements IOfErrorResponseHandler {

    private SpeakerCommandProcessor commandProcessor;

    public MeterInstallCommand(MessageContext messageContext, SwitchId switchId, MeterConfig meterConfig) {
        super(messageContext, switchId, meterConfig);
    }

    @Override
    protected CompletableFuture<MeterInstallReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor)
            throws UnsupportedSwitchOperationException, InvalidMeterIdException {
        this.commandProcessor = commandProcessor;

        ensureSwitchSupportMeters();
        ensureMeterIdValid();

        OFMeterMod meterMod = makeMeterAddMessage();
        return writeSwitchRequest(meterMod)
                .thenApply(ignore -> makeSuccessReport());
    }

    @Override
    protected MeterInstallReport makeReport(Exception error) {
        return new MeterInstallReport(this, error);
    }

    private MeterInstallReport makeSuccessReport() {
        return new MeterInstallReport(this);
    }

    protected CompletableFuture<Optional<OFMessage>> writeSwitchRequest(OFMeterMod request) {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return setupErrorHandler(session.write(request), this);
        }
    }

    @Override
    public CompletableFuture<Optional<OFMessage>> handleOfError(OFErrorMsg response) {
        CompletableFuture<Optional<OFMessage>> future = new CompletableFuture<>();
        if (!isInstallConflict(response)) {
            future.completeExceptionally(new SwitchErrorResponseException(getSw().getId(), String.format(
                    "Can't install meter %s - %s", meterConfig.getId(), response)));
            return future;
        }

        log.info("Meter conflict detected sw:{} meter:{}", getSw().getId(), meterConfig.getId());
        MeterVerifyCommand verifyCommand = new MeterVerifyCommand(messageContext, switchId, meterConfig);
        propagateFutureResponse(
                future, commandProcessor.chain(verifyCommand)
                        .thenAccept(this::handleMeterVerify)
                        .thenApply(ignore -> Optional.empty()));
        return future;
    }

    private boolean isInstallConflict(OFErrorMsg response) {
        if (!(response instanceof OFMeterModFailedErrorMsg)) {
            return false;
        }
        return ((OFMeterModFailedErrorMsg) response).getCode() == OFMeterModFailedCode.METER_EXISTS;
    }

    private void handleMeterVerify(MeterVerifyReport report) {
        try {
            report.raiseError();
            log.warn(
                    "Reuse existing meter sw:{} meter:{} it match with requested meter config - {}",
                    getSw().getId(), meterConfig.getId(), report.getSchema().orElse(null));
        } catch (SwitchIncorrectMeterException | SwitchMissingMeterException e) {
            throw maskCallbackException(new SwitchMeterConflictException(getSw().getId(), meterConfig.getId()));
        } catch (Exception e) {
            throw maskCallbackException(e);
        }
    }

    private void ensureMeterIdValid() throws InvalidMeterIdException {
        MeterId meterId = meterConfig.getId();
        if (meterId == null || meterId.getValue() <= 0L) {
            throw new InvalidMeterIdException(getSw().getId(), String.format(
                    "Invalid meterId value - expect not negative integer, got - %s", meterId));
        }
    }
}
