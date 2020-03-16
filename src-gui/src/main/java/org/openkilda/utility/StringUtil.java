/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.utility;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

/**
 * The Class StringUtil.
 */
public final class StringUtil {

    public static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(11);

    /**
     * Instantiates a new string util.
     */
    private StringUtil() {
    }

    /**
     * Generates random UUIDs.
     * 
     * @return the uuid
     */
    public static String getUuid() {
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
     * Checks if is any null or empty.
     *
     * @param elements the elements
     * @return true, if is any null or empty
     */
    public static boolean isAnyNullOrEmpty(final String... elements) {
        for (String element : elements) {
            if (isNullOrEmpty(element)) {
                return true;
            }
        }
        return false;
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

    /**
     * Gets the encoder.
     *
     * @return the encoder
     */
    public static BCryptPasswordEncoder getEncoder() {
        return encoder;
    }
}
