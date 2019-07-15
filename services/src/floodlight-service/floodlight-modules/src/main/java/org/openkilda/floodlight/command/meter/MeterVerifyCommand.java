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

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.converter.MeterSchemaMapper;
import org.openkilda.floodlight.error.SwitchIncorrectMeterException;
import org.openkilda.floodlight.error.SwitchMissingMeterException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.of.MeterSchema;

import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MeterVerifyCommand extends MeterInstallCommand {
    public MeterVerifyCommand(
            MessageContext messageContext, SwitchId switchId, MeterConfig meterConfig) {
        super(messageContext, switchId, meterConfig);
    }

    @Override
    protected CompletableFuture<MeterReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor)
            throws UnsupportedSwitchOperationException {
        ensureSwitchSupportMeters();

        return new CompletableFutureAdapter<>(
                messageContext, getSw().writeStatsRequest(makeMeterReadCommand()))
                .thenApply(this::handleMeterStats)
                .thenApply(this::makeSuccessReport);
    }

    private OFMeterConfigStatsRequest makeMeterReadCommand() {
        return getSw().getOFFactory().buildMeterConfigStatsRequest()
                .setMeterId(meterConfig.getId().getValue())
                .build();
    }

    private MeterSchema handleMeterStats(List<OFMeterConfigStatsReply> meterStatResponses) {
        Optional<OFMeterConfig> target = Optional.empty();
        for (OFMeterConfigStatsReply meterConfigReply : meterStatResponses) {
            target = findMeter(meterConfigReply);
            if (target.isPresent()) {
                break;
            }
        }

        if (! target.isPresent()) {
            throw maskCallbackException(new SwitchMissingMeterException(getSw().getId(), meterConfig.getId()));
        }

        boolean isInaccurate = getSwitchFeatures().contains(SwitchFeature.INACCURATE_METER);
        MeterSchema schema = MeterSchemaMapper.INSTANCE.map(getSw().getId(), target.get(), isInaccurate);
        validateMeterConfig(schema);

        return schema;
    }

    private Optional<OFMeterConfig> findMeter(OFMeterConfigStatsReply meterConfigReply) {
        MeterId meterId = meterConfig.getId();
        for (OFMeterConfig entry : meterConfigReply.getEntries()) {
            if (meterId.getValue() == entry.getMeterId()) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private void validateMeterConfig(MeterSchema actualSchema) {
        DatapathId datapath = getSw().getId();
        MeterSchema expectedSchema = MeterSchemaMapper.INSTANCE.map(datapath, makeMeterAddMessage());
        if (! expectedSchema.equals(actualSchema)) {
            throw maskCallbackException(new SwitchIncorrectMeterException(datapath, meterConfig, actualSchema));
        }
    }
}
