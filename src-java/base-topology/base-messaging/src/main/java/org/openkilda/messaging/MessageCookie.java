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

package org.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode
public class MessageCookie implements Serializable {
    private static final String FLAT_VIEW_SEPARATOR = " : ";

    @JsonProperty("value")
    String value;

    @EqualsAndHashCode.Exclude
    @JsonProperty("nested")
    MessageCookie nested;

    public MessageCookie(String value) {
        this(value, null);
    }

    @JsonCreator
    public MessageCookie(
            @JsonProperty("value") String value,
            @JsonProperty("nested") MessageCookie nested) {
        this.value = value;
        this.nested = nested;
    }

    /**
     * Flat {@link MessageCookie} representation. Do not supposed to be parsable.
     */
    public String toString() {
        List<String> payload = new ArrayList<>();
        MessageCookie entry = this;
        while (entry != null) {
            payload.add(entry.value);
            entry = entry.nested;
        }
        return "{ " + StringUtils.joinWith(FLAT_VIEW_SEPARATOR, payload.toArray()) + " }";
    }
}
