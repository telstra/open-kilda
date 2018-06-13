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

package org.openkilda.wfm.config;

import com.sabre.oss.conf4j.converter.IntegerConverter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * This class converts integer value of seconds to/from milliseconds.
 */
public class SecondsToMilisConverter extends IntegerConverter {
    @Override
    protected Integer parseWithoutFormat(String value) {
        return super.parseWithoutFormat(value) * 1000;
    }

    @Override
    protected Integer convertResult(Number value) {
        return super.convertResult(value) * 1000;
    }

    @Override
    public String toString(Type type, Integer value, Map<String, String> attributes) {
        return super.toString(type, value / 1000, attributes);
    }
}
