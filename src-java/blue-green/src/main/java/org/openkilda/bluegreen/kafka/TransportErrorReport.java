/* Copyright 2021 Telstra Open Source
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

package org.openkilda.bluegreen.kafka;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;

@Value
public class TransportErrorReport {
    String kafkaTopic;

    String baseClassName;

    String rawSource;

    Exception error;

    public static TransportErrorReport createFromException(
            String kafkaTopic, Class<?> baseClass, byte[] rawSource, Exception error) {
        return createFromException(
                kafkaTopic, baseClass, StringUtils.toEncodedString(rawSource, StandardCharsets.UTF_8), error);
    }

    public static TransportErrorReport createFromException(
            String kafkaTopic, Class<?> baseClass, String rawSource, Exception error) {
        return new TransportErrorReport(kafkaTopic, baseClass.getName(), rawSource, error);
    }
}
