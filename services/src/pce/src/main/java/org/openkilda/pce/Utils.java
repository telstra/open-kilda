/* Copyright 2017 Telstra Open Source
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

package org.openkilda.pce;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.v1.Value;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utils class contains basic utilities and constants.
 */
@Slf4j
public final class Utils {
    /**
     * Return timestamp.
     *
     * @return String timestamp
     */
    public static String getIsoTimestamp() {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
    }

    /**
     * Safe transformation to integer (property value might be stored in neo4j in different types: string, null, etc).
     * @param val the value to parse
     * @return 0 if val is null or not parseable, the int otherwise
     */
    public static int safeAsInt(Value val) {
        int asInt = 0;
        if (!val.isNull()) {
            try {
                asInt = Integer.parseInt(val.asObject().toString());
            } catch (Exception e) {
                log.info("Exception trying to get an Integer; the String isn't parseable. Value: {}", val);
            }
        }
        return asInt;
    }

    private Utils() {
    }
}
