package org.openkilda.utility;

import static java.util.Base64.getEncoder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * The Class Util.
 *
 * @author Gaurav Chugh
 */
@Component
public class Util {

    /** The application properties. */
    @Autowired
    private ApplicationProperties applicationProperties;


    /**
     * Kilda auth header.
     *
     * @return the string
     */
    public String kildaAuthHeader() {
        String auth =
                applicationProperties.getKildaUsername() + ":"
                        + applicationProperties.getKildaPassword();

        return "Basic " + getEncoder().encodeToString(auth.getBytes());

    }


    /**
     * Checks if is object empty.
     *
     * @param object the object
     * @return true, if is object empty
     */
    public static boolean isObjectEmpty(final Object object) {
        if (object == null) {
            return true;
        } else if (object instanceof String) {
            return StringUtil.isNullOrEmpty((String)object);
        } else if (object instanceof Collection) {
            return CollectionUtil.isEmpty((Collection<?>) object);
        }
        return false;
    }
}
