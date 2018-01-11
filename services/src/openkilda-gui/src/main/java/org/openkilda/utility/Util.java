package org.openkilda.utility;

import static java.util.Base64.getEncoder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The Class Util.
 * 
 * @author Gaurav Chugh
 */
@Component
public class Util {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(Util.class);

    /** The application properties. */
    @Autowired
    ApplicationProperties applicationProperties;

    /** The Constant bCryptPasswordEncoder. */
    public static final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder(11);

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
     * Generates random UUIDs.
     * 
     * @return the uuid
     */
    public static String getUUID() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    /**
     * Concatenate.
     *
     * @param listOfItems the list of items
     * @param separator the separator
     * @return the string
     */
    public static String concatenate(List<String> listOfItems, String separator) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> stit = listOfItems.iterator();

        while (stit.hasNext()) {
            sb.append(stit.next());
            if (stit.hasNext()) {
                sb.append(separator);
            }
        }

        return sb.toString();
    }

    /**
     * Checks if is collection empty.
     *
     * @param collection the collection
     * @return true, if is collection empty
     */
    private static boolean isCollectionEmpty(Collection<?> collection) {
        if (collection == null || collection.isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * Checks if is object empty.
     *
     * @param object the object
     * @return true, if is object empty
     */
    public static boolean isObjectEmpty(Object object) {
        if (object == null)
            return true;
        else if (object instanceof String) {
            if (((String) object).trim().length() == 0) {
                return true;
            }
        } else if (object instanceof Collection) {
            return isCollectionEmpty((Collection<?>) object);
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
        try {
            return new String(bCryptPasswordEncoder.encode(data));
        } catch (Exception e) {
            LOG.error(e.getLocalizedMessage(), e);
        }

        return null;
    }

    /**
     * Matches.
     *
     * @param rawPassword the raw password
     * @param encodedPassword the encoded password
     * @return true, if successful
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return bCryptPasswordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * Format date.
     *
     * @param date the date
     * @param locale the locale
     * @return Date formated for UI.
     */
    public static String formatDate(Date date, Locale locale) {
        String retVal = " - ";

        if (date != null) {
            DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, locale);
            retVal = dateFormat.format(date);
        } else {
            LOG.warn("formatDate(). date == null");
        }
        return retVal;
    }

    /**
     * Method to parse string into date.
     *
     * @param dateToFormat the date to format
     * @return the date
     */
    public static Date stringToDate(String dateToFormat) {
        try {
            DateFormat format = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
            return format.parse(dateToFormat);
        } catch (Exception e) {
            LOG.warn("toDate(). date couldnt parsed" + e);
        }
        return null;
    }

    /**
     * Date to string.
     *
     * @param date the date
     * @return the string
     */
    public static String dateToString(Date date) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");
            return dateFormat.format(date);
        } catch (Exception e) {
            LOG.warn("dateToString(). date couldnt parsed" + e);
        }
        return null;
    }

    /**
     * TODO: method to return default date, as of now today's date is default date.
     *
     * @return the default date
     */
    public static Date getDefaultDate() {
        return new Date();
    }

}
