package org.openkilda.wfm.protocol;

import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.wfm.error.MessageFormatException;

import java.io.IOException;

public class KafkaMessage extends JsonMessage<Message> {
    public KafkaMessage(Tuple raw) throws MessageFormatException {
        super(raw);
    }

    public KafkaMessage(Message payload) {
        super(payload);
    }

    @Override
    protected Message unpackJson(String json) throws IOException {
        return Utils.MAPPER.readValue(json, Message.class);
    }
}
