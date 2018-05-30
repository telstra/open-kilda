package org.openkilda.wfm.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.wfm.error.MessageFormatException;

abstract public class BoltToBoltMessage<T> extends JsonMessage<T> {
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
