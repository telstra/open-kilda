package org.openkilda.wfm.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.tuple.Fields;
import org.openkilda.wfm.ImplementationError;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractMessage {
    public List<Object> pack() throws JsonProcessingException {
        Fields format = getFormat();
        Object packed[] = new Object[format.size()];

        for (int i = 0; i < packed.length; i++) {
            packed[i] = packField(format.get(i));
        }

        return Arrays.asList(packed);
    }

    protected Object packField(String fieldId) throws JsonProcessingException {
        throw new ImplementationError(String.format(
                "Class %s does not implement packField(\"%s\")",
                getClass(), fieldId));
    }

    abstract protected Fields getFormat();
}
