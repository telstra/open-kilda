package org.openkilda.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
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
     */
    public static String toString(final Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static <T> T toObject(final String data, final Class<T> objClass) {
        try {
            return mapper.readValue(data, objClass);
        } catch (IOException e) {
            return null;
        }
    }
}
