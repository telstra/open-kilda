package org.usermanagement.util;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * The Class Validator.
 */
public final class ValidatorUtil {


    /**
     * Checks if is null or empty.
     *
     * @param obj the obj
     * @return true, if is null or empty
     */
    public static boolean isNullOrEmpty(Object obj) {
        return isNull(obj);
    }

    
    /**
     * Checks if is numeric.
     *
     * @param value the value
     * @return the boolean
     */
    public static Boolean isNumeric(Object value) {
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
    public static boolean validLength(String val, Integer permitLength) {
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
    public static boolean validIntRange(Integer val, Integer min, Integer max) {
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
    public static Boolean isInteger(Object value) {
        if (value instanceof Integer) {
            return true;
        }
        return false;
    }
    
    /** The Constant UUID_PATTERN. */
    public static final String UUID_PATTERN = "^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$";
    
    /** The Constant NUMBER_PATTERN. */
    public static final String NUMBER_PATTERN = "^[0-9]*$";

    /**
     * Checks if is valid uuid.
     *
     * @param uuId the uu id
     * @return the boolean
     */
    public static Boolean isValidUuid(String uuId) {
        boolean isUuidValid = false;
        uuId = uuId.trim();
        if (uuId.matches("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")) {
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
    public static Boolean isValidNumber(String val) {
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
    public static boolean isNull(Object obj) {
        if (obj == null) {
            return true;
        } else if (obj instanceof Collection) {
            Collection string2 = (Collection) obj;
            return string2.isEmpty();
        } else if (obj instanceof Map) {
            Map string1 = (Map) obj;
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
    public static boolean validateEmail(String email) {
        String EmailPattern = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
        Pattern pattern = Pattern
                .compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");
        Matcher matcher = pattern.matcher(email.trim());
        return matcher.matches();
    }
    
    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@.#$%&*";
    
    public static String randomAlphaNumeric(int count) {
    StringBuilder builder = new StringBuilder();
    while (count-- != 0) {
    int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
    builder.append(ALPHA_NUMERIC_STRING.charAt(character));
    }
    return builder.toString();
    }
 
}
