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

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchId;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterMod;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Complete equivalent to `install` command, but do not write meter-mod message into switch. Usefull to check switch
 * capabilities i.e. is it support meters (and all required meter flags).
 */
public class MeterInstallDryRunCommand extends MeterInstallCommand {
    public MeterInstallDryRunCommand(MessageContext messageContext, SwitchId switchId, MeterConfig meterConfig) {
        super(messageContext, switchId, meterConfig);
    }

    @Override
    protected CompletableFuture<Optional<OFMessage>> writeSwitchRequest(OFMeterMod request) {
        // dry-run - do not write anything to switch
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
