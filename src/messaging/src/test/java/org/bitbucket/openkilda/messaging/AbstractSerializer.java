package org.bitbucket.openkilda.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public interface AbstractSerializer {
    ObjectMapper mapper = new ObjectMapper();
    Object deserialize() throws IOException, ClassNotFoundException;
    void serialize(Object obj) throws IOException;
}
