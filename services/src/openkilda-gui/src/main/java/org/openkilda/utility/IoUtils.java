package org.openkilda.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * The Class IoUtils.
 */
public final class IoUtils {

    /** The Constant _log. */
    private static final Logger _log = LoggerFactory.getLogger(IoUtils.class);

    /**
     * Instantiates a new io utils.
     */
    private IoUtils() {

    }

    /**
     * Returns data present in the stream.
     *
     * @param inputStream input stream having content.
     * @return data with all content present in the stream.
     * @throws IOException If an I/O error occurs
     */
    public static String toString(final InputStream inputStream) throws IOException {
        _log.debug("[getData] - Start");
        StringBuilder data = new StringBuilder();
        try(BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = null;
            while ((line = rd.readLine()) != null) {
                if (line != null && !line.isEmpty()) {
                    data.append(line);
                }
            }
        }
        _log.debug("[getData] Data: " + data);
        _log.debug("[getData] - End  ");
        return data.toString();
    }

    /**
     * Close the closable object.
     *
     * @param closeable closable object.
     */
    public static void close(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Do Nothing
            }
        }
    }

    /**
     * Gets the parameter.
     *
     * @param <T> the generic type
     * @param parameters the parameters
     * @param key the key
     * @param responseClass the response class
     * @return the parameter
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(final Map<String, Object> parameters, final String key,
            final Class<T> responseClass) {
        return (T) parameters.get(key);
    }
}
