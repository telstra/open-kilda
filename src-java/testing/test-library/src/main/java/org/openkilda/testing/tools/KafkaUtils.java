/* Copyright 2022 Telstra Open Source
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

package org.openkilda.testing.tools;

import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.SpeakerData;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Helper class to build kafka messages.
 */
public final class KafkaUtils {

    private KafkaUtils() {}

    public static AbstractMessage buildMessage(SpeakerData speakerData) {
        return buildMessage(Collections.singletonList(speakerData));
    }

    /**
     * Build install speaker commands request.
     */
    public static AbstractMessage buildMessage(List<SpeakerData> speakerData) {
        return InstallSpeakerCommandsRequest.builder()
                .messageContext(new MessageContext())
                .switchId(speakerData.get(0).getSwitchId())
                .commandId(UUID.randomUUID())
                .commands(OfCommand.toOfCommands(speakerData))
                .build();
    }

    /**
     * Build flow segment cookie.
     */
    public static Cookie buildCookie(long baseCookie) {
        return FlowSegmentCookie.builder()
                .direction(FlowPathDirection.FORWARD)
                .flowEffectiveId(baseCookie)
                .build();
    }
}
