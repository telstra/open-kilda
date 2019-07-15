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

package org.openkilda.floodlight.api.response;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.util.UUID;

@Getter
public class SpeakerErrorResponse extends SpeakerResponse {
    @JsonProperty("error_code")
    private final SpeakerErrorCode errorCode;

    @JsonProperty("details")
    private final String details;

    @Builder
    @JsonCreator
    public SpeakerErrorResponse(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("switch_id") SwitchId switchId,
            @JsonProperty("error_code") @NonNull SpeakerErrorCode errorCode,
            @JsonProperty("details") String details) {
        super(messageContext, commandId, switchId);
        this.errorCode = errorCode;
        this.details = ensureDetailsValue(details, errorCode);
    }

    private static String ensureDetailsValue(String details, SpeakerErrorCode errorCode) {
        if (! Strings.isNullOrEmpty(details)) {
            return details;
        }

        String defaultDetails;
        switch (errorCode) {
            case SWITCH_UNAVAILABLE:
                defaultDetails = "Switch is unavailable";
                break;
            case UNKNOWN:
                defaultDetails = "Unknown error";
                break;
            default:
                throw new IllegalArgumentException(String.format(
                        "Unknown error code %s.%s, can't translate it into error description",
                        errorCode.getClass().getName(), errorCode));
        }

        return defaultDetails;
    }
}
