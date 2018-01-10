package org.openkilda.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class StringUtils.
 */
public final class StringUtils {

    /** The mapper. */
    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * Instantiates a new string utils.
     */
    private StringUtils() {}

    /**
     * Checks if is null or empty.
     *
     * @param data the data
     * @return true, if is null or empty
     */
    public static boolean isNullOrEmpty(final String data) {
        return data == null || data.trim().isEmpty();
    }

    /**
     * To json string.
     *
     * @param obj the obj
     * @return the string
     */
    public static String toJsonString(final Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
