package org.openkilda.utility;

import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * The Class StringUtil.
 */
public final class StringUtil {

    public static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(11);

    /**
     * Instantiates a new string util.
     */
    private StringUtil() {}
    
    /**
     * Generates random UUIDs.
     * 
     * @return the uuid
     */
    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

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
     * Encode string.
     *
     * @param data the data
     * @return the string
     */
    public static String encodeString(String data) {
    	return new String(encoder.encode(data));
    }
    
    /**
     * Matches.
     *
     * @param rawPassword the raw password
     * @param encodedPassword the encoded password
     * @return true, if successful
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
    
    public static BCryptPasswordEncoder getEncoder() {
		return encoder;
	}
}
