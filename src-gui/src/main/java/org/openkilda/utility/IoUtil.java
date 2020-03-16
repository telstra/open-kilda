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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Predicate;

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
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream))) {
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

    /**
     * Chk string is not empty.
     *
     * @param value the value
     * @return true, if successful
     */
    public static boolean chkStringIsNotEmpty(String value) {
        if (value != null) {
            Predicate<String> predicates = s -> {
                return value.trim().length() > 0;
            };
            return predicates.test(value);
        }
        return false;
    }
    
    /**
     * Switch code to switch id.
     *
     * @param switchCode the switch code
     * @return the string
     */
    public static String switchCodeToSwitchId(String switchCode) {
        if (!StringUtil.isNullOrEmpty(switchCode)) {
            if (switchCode.contains("SW")) {
                switchCode = switchCode.replace("SW", "");
                StringBuffer switchId = new StringBuffer();
                char[] code = switchCode.toCharArray();
                for (int i = 0; i < code.length; i++) {
                    switchId.append(code[i]);
                    if ((i != 0) && (i % 2 != 0) && (i != code.length - 1)) {
                        switchId.append(":");
                    }
                }
                return switchId.toString();
            }
        }
        return switchCode;
    }
}
