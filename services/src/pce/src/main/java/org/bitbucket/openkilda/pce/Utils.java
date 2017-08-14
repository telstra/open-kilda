package org.bitbucket.openkilda.pce;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utils class contains basic utilities and constants.
 */
public final class Utils {
    /**
     * Return timestamp.
     *
     * @return String timestamp
     */
    public static String getIsoTimestamp() {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
    }
}
