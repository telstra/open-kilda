package org.bitbucket.openkilda.messaging;

import java.io.IOException;

public interface AbstractSerializer {
    Object deserialize() throws IOException, ClassNotFoundException;

    void serialize(Object obj) throws IOException;
}
