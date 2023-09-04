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

package org.openkilda.messaging.info;

import org.openkilda.messaging.MessageData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

/**
 * Defines the payload of a Message representing an info.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public abstract class InfoData extends MessageData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    private long interceptorInTimestamp = 0;
    private long interceptorOutTimestamp = 0;
    private long parseOutTimestamp = 0;
    private long filterInTimestamp = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("Not implemented for: %s", getClass().getSimpleName());
    }
}
