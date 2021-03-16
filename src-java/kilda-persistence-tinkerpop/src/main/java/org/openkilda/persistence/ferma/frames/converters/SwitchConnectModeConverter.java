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

package org.openkilda.persistence.ferma.frames.converters;

import org.openkilda.model.SwitchConnectMode;

public class SwitchConnectModeConverter implements AttributeConverter<SwitchConnectMode, String> {
    public static final SwitchConnectModeConverter INSTANCE = new SwitchConnectModeConverter();

    @Override
    public String toGraphProperty(SwitchConnectMode value) {
        if (value == null) {
            return null;
        }
        return value.toString().toLowerCase();
    }

    @Override
    public SwitchConnectMode toEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return SwitchConnectMode.valueOf(value.toUpperCase());
    }
}
