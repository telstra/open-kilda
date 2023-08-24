/* Copyright 2023 Telstra Open Source
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

import org.openkilda.model.history.DumpType;

public class DumpTypeConverter implements AttributeConverter<DumpType, String> {

    @Override
    public String toGraphProperty(DumpType value) {
        return value == null ? null : value.getType();
    }

    @Override
    public DumpType toEntityAttribute(String value) {
        return value == null ? null : DumpType.of(value);
    }
}
