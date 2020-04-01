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

package org.usermanagement.util;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Class Validator.
 */

public final class ValidatorUtil {
    
    private ValidatorUtil() {
    }

    /**
     * Checks if is null or empty.
     *
     * @param obj the obj
     * @return true, if is null or empty
     */
    public static boolean isNullOrEmpty(final Object obj) {
        return isNull(obj);
    }

    /**
     * Checks if is numeric.
     *
     * @param value the value
     * @return the boolean
     */
    public static Boolean isNumeric(final Object value) {
        boolean isNumeric = false;
        if ((value.toString()).matches(NUMBER_PATTERN)) {
            isNumeric = true;
        }
        return isNumeric;
    }

    /**
     * Valid length.
     *
     * @param val the val
     * @param permitLength the permit length
     * @return true, if successful
     */
    public static boolean validLength(final String val, final Integer permitLength) {
        if (val.length() <= permitLength) {
            return true;
        }
        return false;
    }

    /**
     * Valid int range.
     *
     * @param val the val
     * @param min the min
     * @param max the max
     * @return true, if successful
     */
    public static boolean validIntRange(final Integer val, final Integer min, final Integer max) {
        if ((val >= min) && (val <= max)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if is integer.
     *
     * @param value the value
     * @return the boolean
     */
    public static Boolean isInteger(final Object value) {
        if (value instanceof Integer) {
            return true;
        }
        return false;
    }

    /** The Constant UUID_PATTERN. */
    public static final String UUID_PATTERN = 
            "^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$";

    /** The Constant NUMBER_PATTERN. */
    public static final String NUMBER_PATTERN = "^[0-9]*$";

    /**
     * Checks if is valid uuid.
     *
     * @param uuid the uuid
     * @return the boolean
     */
    public static Boolean isValidUuid(final String uuid) {
        boolean isUuidValid = false;
        if (uuid.trim().matches("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")) {
            isUuidValid = true;
        }
        return Boolean.valueOf(isUuidValid);
    }

    /**
     * Checks if is valid number.
     *
     * @param val the val
     * @return the boolean
     */
    public static Boolean isValidNumber(final String val) {
        try {
            Integer.parseInt(val);
            if (val.startsWith("-")) {
                return Boolean.valueOf(false);
            }
        } catch (Exception arg1) {
            return Boolean.valueOf(false);
        }

        return Boolean.valueOf(true);
    }

    /**
     * Checks if is null.
     *
     * @param obj the obj
     * @return true, if is null
     */
    public static boolean isNull(final Object obj) {
        if (obj == null) {
            return true;
        } else if (obj instanceof Collection) {
            Collection<?> string2 = (Collection<?>) obj;
            return string2.isEmpty();
        } else if (obj instanceof Map) {
            Map<?, ?> string1 = (Map<?, ?>) obj;
            return string1.isEmpty();
        } else if (obj instanceof String) {
            String string = ((String) obj).trim();
            return string.isEmpty();
        } else {
            return false;
        }
    }

    /**
     * Validate email.
     *
     * @param email the email
     * @return true, if successful
     */
    public static boolean validateEmail(final String email) {
        String emailPattern = 
                "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
        Pattern pattern = Pattern.compile(emailPattern);
        Matcher matcher = pattern.matcher(email.trim());
        return matcher.matches();
    }

    private static final String ALPHA_NUMERIC_STRING =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$&*_-";

    /**
     * Random alpha numeric.
     *
     * @param count the count
     * @return the string
     */
    public static String randomAlphaNumeric(final int count) {
        StringBuilder builder = new StringBuilder();
        int result = count;
        while (result-- != 0) {
            int character = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
}
