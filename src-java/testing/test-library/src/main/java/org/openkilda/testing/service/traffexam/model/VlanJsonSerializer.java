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

package org.openkilda.testing.service.traffexam.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.List;

public class VlanJsonSerializer extends JsonSerializer<List<Vlan>> {

    @Override
    public void serialize(List<Vlan> vlan, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (vlan == null) {
            jsonGenerator.writeNull();
        } else {
            int[] numbers = vlan.stream().mapToInt(Vlan::getVlanTag).filter(x -> x != 0).toArray();
            jsonGenerator.writeArray(numbers, 0, numbers.length);
        }
    }
}
