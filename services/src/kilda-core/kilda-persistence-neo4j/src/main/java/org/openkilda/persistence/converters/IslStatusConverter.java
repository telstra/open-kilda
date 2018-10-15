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

package org.openkilda.persistence.converters;

import org.openkilda.model.IslStatus;

import org.neo4j.ogm.typeconversion.AttributeConverter;

/**
 * Case-insensitive converter to convert {@link IslStatus} to {@link String}.
 */
public class IslStatusConverter implements AttributeConverter<IslStatus, String> {

    @Override
    public String toGraphProperty(IslStatus value) {
        if (value == null) {
            return null;
        }
        return value.name().toLowerCase();
    }

    @Override
    public IslStatus toEntityAttribute(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return IslStatus.valueOf(value.toUpperCase());
    }
}
