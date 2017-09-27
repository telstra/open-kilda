package org.bitbucket.openkilda.messaging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

public interface ByteArraySerializer extends AbstractSerializer {
    ByteBuffer byteBuffer = ByteBuffer.allocate(4096);

    @Override
    default Object deserialize() throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(byteBuffer.array());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object obj = ois.readObject();
        ois.close();
        bais.close();
        byteBuffer.clear();
        return obj;
    }

    @Override
    default void serialize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.flush();
        byteBuffer.put(baos.toByteArray());
        oos.close();
        baos.close();
    }
}
