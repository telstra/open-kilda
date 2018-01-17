package org.openkilda.utility;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * The Class IoUtils.
 */
public final class IoUtil {

    /**
     * Instantiates a new io utils.
     */
    private IoUtil() {

    }

    /**
     * Returns data present in the stream.
     *
     * @param inputStream input stream having content.
     * @return data with all content present in the stream.
     * @throws IOException If an I/O error occurs
     */
    public static String toString(final InputStream inputStream) throws IOException {
        StringBuilder data = new StringBuilder();
        try(BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = null;
            while ((line = rd.readLine()) != null) {
                if (!line.isEmpty()) {
                    data.append(line);
                }
            }
        }
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
}
