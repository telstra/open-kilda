/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.protocol;

import org.openkilda.wfm.error.MessageFormatException;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

public abstract class BoltToBoltMessage<T> extends JsonMessage<T> {
    public static String FIELD_ID_CORRELATION_ID = "Correlation-Id";

    public static Fields FORMAT = new Fields(
            FIELD_ID_JSON, FIELD_ID_CORRELATION_ID);

    private final String correlationId;

    public BoltToBoltMessage(Tuple raw) throws MessageFormatException {
        super(raw);

        correlationId = raw.getString(
                getFormat().fieldIndex(FIELD_ID_CORRELATION_ID));
    }

    public BoltToBoltMessage(T payload, String correlationId) {
        super(payload);
        this.correlationId = correlationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    protected Object packField(String fieldId) throws JsonProcessingException {
        if (fieldId.equals(FIELD_ID_CORRELATION_ID)) {
            return getCorrelationId();
        }
        return super.packField(fieldId);
    }

    @Override
    protected Fields getFormat() {
        return FORMAT;
    }
}
