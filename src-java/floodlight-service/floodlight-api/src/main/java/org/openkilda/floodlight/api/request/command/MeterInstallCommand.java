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

package org.openkilda.floodlight.api.request.command;

import org.openkilda.floodlight.api.OfSpeaker;
import org.openkilda.messaging.MessageContext;
import org.openkilda.rulemanager.MeterSpeakerCommandData;

import lombok.Getter;

import java.util.concurrent.CompletableFuture;

@Getter
public class MeterInstallCommand extends MeterCommandBase {
    public MeterInstallCommand(MessageContext messageContext, MeterSpeakerCommandData payload) {
        super(messageContext, payload);
    }

    @Override
    public CompletableFuture<MessageContext> execute(OfSpeaker speaker) {
        return speaker.installMeter(
                getMessageContext(), getSwitchId(), meterData.getMeterId(), meterData.getRate(), meterData.getBurst());
    }
}
