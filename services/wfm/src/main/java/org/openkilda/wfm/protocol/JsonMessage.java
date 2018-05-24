package org.openkilda.wfm.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.Utils;
import org.openkilda.wfm.error.MessageFormatException;

import java.io.IOException;

abstract public class JsonMessage<T> extends AbstractMessage {
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
