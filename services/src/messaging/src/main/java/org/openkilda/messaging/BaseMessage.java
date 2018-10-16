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

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * BaseMessage is the base class for all OpenKilda messages. There are several use cases we can
 * solve with a common base class:
 * <p/>
 * (1) we can use it for message deserialization everywhere, guaranteeing that that we have a
 * known entity, and any failure to deserialize is really a failure and worthy of a Warning.
 * (2) we can introduce common functionality that we'd like to have - Destination / Source /
 * Return information. Possibly desired kilda topic to help with traceability.
 * <p/>
 * Initial base member will have a timestamp field.
 */
@EqualsAndHashCode
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = PROPERTY, property = "clazz")
public abstract class BaseMessage implements Serializable {
    /**
     * Serialization version number constant.
     */
    static final long serialVersionUID = 1L;

    /**
     * Message timestamp.
     */
    @JsonProperty(TIMESTAMP)
    protected long timestamp = 0L;


    /**
     * Instance constructor.
     *
     * @param timestamp     message timestamp
     */
    @JsonCreator
    public BaseMessage(@JsonProperty(TIMESTAMP) final long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Create a BaseMessage with the current time as the timestamp.
     */
    public BaseMessage() {
        this(System.currentTimeMillis());
    }


    /**
     * Returns message timestamp.
     *
     * @return message timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp.
     *
     * @param timestamp The timestamp, eg System.currentTimeMillis()
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
