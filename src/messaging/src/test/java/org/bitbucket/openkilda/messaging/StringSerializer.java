package org.bitbucket.openkilda.messaging;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public interface StringSerializer extends AbstractSerializer {
    Queue<String> strings = new LinkedList<>();

    @Override
    default Object deserialize() throws IOException {
        return mapper.readValue(strings.poll(), Message.class);
    }

    @Override
    default void serialize(Object object) throws IOException {
        strings.add(mapper.writeValueAsString(object));
    }
}
