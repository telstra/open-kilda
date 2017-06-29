package org.bitbucket.openkilda.messaging;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public interface StringSerializer extends AbstractSerializer {
    Queue<String> strings = new LinkedList<>();

    @Override
    default Object deserialize() throws IOException {
        return MAPPER.readValue(strings.poll(), Message.class);
    }

    @Override
    default void serialize(Object object) throws IOException {
        strings.add(MAPPER.writeValueAsString(object));
    }
}
