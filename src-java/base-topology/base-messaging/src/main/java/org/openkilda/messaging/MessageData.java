/* Copyright 2017 Telstra Open Source
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

import static org.openkilda.messaging.Utils.TIMESTAMP;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

/**
 * Class represents high level view of payload for every message used by any service.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class MessageData extends BaseMessage {
    /**
     * Serialization version number constant.
     */
    static final long serialVersionUID = 1L;

    @JsonCreator
    public MessageData(@JsonProperty(TIMESTAMP) final long timestamp) {
        super(timestamp);
    }

    public MessageData() {
        super(System.currentTimeMillis());
    }

}
