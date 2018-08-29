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

import org.openkilda.messaging.Utils;
import org.openkilda.wfm.error.MessageFormatException;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.io.IOException;

public abstract class JsonMessage<T> extends AbstractMessage {
    public static final String FIELD_ID_JSON = "json";

    public static final Fields FORMAT = new Fields(FIELD_ID_JSON);

    private T payload;

    public JsonMessage(Tuple raw) throws MessageFormatException {
        super();

        String json = raw.getString(getFormat().fieldIndex(FIELD_ID_JSON));
        try {
            payload = unpackJson(json);
        } catch (IOException e) {
            throw new MessageFormatException(raw, e);
        }
    }

    public JsonMessage(T payload) {
        this.payload = payload;
    }

    protected abstract T unpackJson(String json) throws IOException;

    public T getPayload() {
        return payload;
    }

    @Override
    protected Object packField(String fieldId) throws JsonProcessingException {
        if (fieldId.equals(FIELD_ID_JSON)) {
            return Utils.MAPPER.writeValueAsString(getPayload());
        }
        return super.packField(fieldId);
    }

    @Override
    protected Fields getFormat() {
        return FORMAT;
    }
}
