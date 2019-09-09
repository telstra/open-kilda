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

import static java.util.Collections.singletonList;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_13;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.command.IdempotentMessageWriter;
import org.openkilda.floodlight.command.IdempotentMessageWriter.ErrorTypeHelper;
import org.openkilda.floodlight.command.SessionProxy;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.error.InvalidMeterIdException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFMeterModFailedCode;
import org.projectfloodlight.openflow.protocol.errormsg.OFMeterModFailedErrorMsg;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBandDrop;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class InstallMeterCommand extends MeterCommand {

    private Long bandwidth;

    public InstallMeterCommand(@JsonProperty("message_context") MessageContext messageContext,
                               @JsonProperty("switch_id") SwitchId switchId,
                               @JsonProperty("meter_id") MeterId meterId,
                               @JsonProperty("bandwidth") Long bandwidth) {
        super(switchId, messageContext, meterId);
        this.bandwidth = bandwidth;
    }

    @Override
    protected FloodlightResponse buildError(Throwable error) {
        return null;
    }

    @Override
    protected FloodlightResponse buildResponse() {
        return null;
    }

    @Override
    public List<SessionProxy> getCommands(IOFSwitch sw, FloodlightModuleContext moduleContext)
            throws SwitchOperationException {
        FeatureDetectorService featureDetectorService = moduleContext.getServiceImpl(FeatureDetectorService.class);
        FloodlightModuleConfigurationProvider provider =
                FloodlightModuleConfigurationProvider.of(moduleContext, SwitchManager.class);
        SwitchManagerConfig switchManagerConfig = provider.getConfiguration(SwitchManagerConfig.class);

        OFMeterMod meterInstallCommand = buildMeter(switchManagerConfig, featureDetectorService, sw);

        return Collections.singletonList(IdempotentMessageWriter.<OFMeterConfigStatsReply>builder()
                .context(messageContext)
                .message(meterInstallCommand)
                .readRequest(getMeterRequest(sw.getOFFactory()))
                .ofEntryChecker(new MeterChecker(meterInstallCommand))
                .errorTypeHelper(new MeterInstallOfErrorHelper())
                .build());
    }

    private OFMeterMod buildMeter(SwitchManagerConfig config, FeatureDetectorService featureDetectorService,
                                 IOFSwitch sw) throws UnsupportedSwitchOperationException, InvalidMeterIdException {
        checkSwitchSupportCommand(sw, featureDetectorService);

        if (meterId != null && meterId.getValue() > 0L) {
            long burstSize = Meter.calculateBurstSize(bandwidth, config.getFlowMeterMinBurstSizeInKbits(),
                    config.getFlowMeterBurstCoefficient(), sw.getSwitchDescription().getManufacturerDescription(),
                    sw.getSwitchDescription().getSoftwareDescription());

            Set<OFMeterFlags> flags = ImmutableSet.of(OFMeterFlags.KBPS, OFMeterFlags.BURST, OFMeterFlags.STATS);
            return getMeter(sw, flags, burstSize);
        } else {
            throw new InvalidMeterIdException(sw.getId(), "Meter id must be positive.");
        }
    }

    private OFMeterMod getMeter(IOFSwitch sw, Set<OFMeterFlags> flags, long burstSize) {
        OFFactory ofFactory = sw.getOFFactory();

        // NB: some switches might replace 0 burst size value with some predefined value
        OFMeterBandDrop.Builder bandBuilder = ofFactory.meterBands()
                .buildDrop()
                .setRate(bandwidth)
                .setBurstSize(burstSize);

        OFMeterMod.Builder meterModBuilder = ofFactory.buildMeterMod()
                .setMeterId(meterId.getValue())
                .setCommand(OFMeterModCommand.ADD)
                .setFlags(flags);

        if (sw.getOFFactory().getVersion().compareTo(OF_13) > 0) {
            meterModBuilder.setBands(singletonList(bandBuilder.build()));
        } else {
            meterModBuilder.setMeters(singletonList(bandBuilder.build()));
        }

        return meterModBuilder.build();
    }

    class MeterInstallOfErrorHelper implements ErrorTypeHelper {
        @Override
        public boolean alreadyExists(OFErrorMsg errorMsg) {
            OFMeterModFailedErrorMsg meterModError = (OFMeterModFailedErrorMsg) errorMsg;
            return meterModError.getCode() == OFMeterModFailedCode.METER_EXISTS;
        }
    }

    final OFMeterConfigStatsRequest getMeterRequest(OFFactory ofFactory) {
        return ofFactory.buildMeterConfigStatsRequest()
                .setMeterId(meterId.getValue())
                .build();
    }

}
