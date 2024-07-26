/* Copyright 2024 Telstra Open Source
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

package org.openkilda.bluegreen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.UUID;

class UuidKryoSerializerTest {

    @Test
    void roundTrip() {
        Kryo kryo = new Kryo();

        kryo.register(UUID.class, new UuidKryoSerializer());

        try (Output output = new Output(100)) {
            UUID source = new UUID(100L, 50L);
            kryo.writeClassAndObject(output, source);

            Object o = kryo.readClassAndObject(new Input(new ByteArrayInputStream(output.toBytes())));
            assertInstanceOf(UUID.class, o);
            assertEquals(source, o);
        }
    }
}
