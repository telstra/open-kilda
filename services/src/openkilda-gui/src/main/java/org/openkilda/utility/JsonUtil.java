package org.openkilda.utility;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public final class JsonUtil {

	private static ObjectMapper mapper = new ObjectMapper();

	private JsonUtil() {
	}


	/**
     * To json string.
     *
     * @param obj the obj
     * @return the string
	 * @throws JsonProcessingException
     */
    public static String toString(final Object obj) throws JsonProcessingException {
        return mapper.writeValueAsString(obj);
    }

    public static <T> T toObject(final String data, final Class<T> objClass) throws JsonParseException, JsonMappingException, IOException {
        return mapper.readValue(data, objClass);
    }
}
