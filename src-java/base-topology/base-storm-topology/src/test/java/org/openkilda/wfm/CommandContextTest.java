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

package org.openkilda.wfm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

@Slf4j
class CommandContextTest {

    private Kryo kryo;

    @BeforeEach
    public void init() {
        kryo = new Kryo();
    }

    @AfterEach
    public void teardown() {
        kryo = null;
    }

    private static final int randomElements = 10_000;
    private static final int serializationRepeatTimes = 1_000_000;

    @Test
    void serializationTestWithKryoBeanSerializer() {
        Collection<CommandContext> commands = getCommandContexts(randomElements);

        kryo.register(CommandContext.class);


        long duration = performSerialization(commands, serializationRepeatTimes);
        log.info("Duration of {} serialization {} is: {} ms", "KryoBeanSerializer", randomElements, duration);

        assertTrue(duration > 0);
    }

    @Test
    void serializationTestWithKryoFallback() {
        Collection<CommandContext> commands = getCommandContexts(randomElements);

        kryo.register(CommandContext.class, new JavaSerializer());

        long duration = performSerialization(commands, serializationRepeatTimes);
        log.info("Duration of {} serialization {} is: {} ms", "KryoFallback", randomElements, duration);

        assertTrue(duration > 0);
    }

    @Test
    void serializationNativeJavaTest() throws Exception {
        Collection<CommandContext> commands = getCommandContexts(randomElements);

        long duration = performSerializationJava(commands, serializationRepeatTimes);
        log.info("Duration of {} serialization {} is: {} ms", "NativeJava", randomElements, duration);

        assertTrue(duration > 0);
    }

    private long performSerializationJava(Collection<CommandContext> commands, int times) throws Exception {
        Instant start = Instant.now();
        Iterator<CommandContext> iterator = commands.iterator();
        do {
            if (!iterator.hasNext()) {
                iterator = commands.iterator();
            }
            CommandContext commandContext = iterator.next();

            byte[] buffer;
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream outputStream = new ObjectOutputStream(byteOutputStream);
            outputStream.writeObject(commandContext);
            outputStream.flush();
            buffer = byteOutputStream.toByteArray();
            byteOutputStream.close();
            outputStream.close();

            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(buffer);
            ObjectInputStream inputStream = new ObjectInputStream(byteInputStream);
            CommandContext read = (CommandContext) inputStream.readObject();
            byteInputStream.close();
            inputStream.close();
            assertNotNull(read);
            assertEquals(commandContext.getKafkaTopic(), read.getKafkaTopic());
        } while (times-- > 0);
        return Duration.between(start, Instant.now()).toMillis();
    }

    private long performSerialization(Collection<CommandContext> commands, int times) {
        Instant start = Instant.now();
        Iterator<CommandContext> iterator = commands.iterator();
        do {
            byte[] buffer = new byte[randomElements * 10];
            if (!iterator.hasNext()) {
                iterator = commands.iterator();
            }
            CommandContext commandContext = iterator.next();

            try (Output output = new Output(buffer)) {
                kryo.writeObject(output, commandContext);
                assertTrue(output.toBytes().length > 0);
            }
            try (Input input = new Input(buffer)) {
                CommandContext read = kryo.readObject(input, CommandContext.class);
                assertNotNull(read);
                assertEquals(commandContext.getKafkaTopic(), read.getKafkaTopic());
            }
        } while (times-- > 0);
        return Duration.between(start, Instant.now()).toMillis();
    }

    private Collection<CommandContext> getCommandContexts(int times) {
        Collection<CommandContext> commands = new ArrayList<>(times);
        do {
            CommandContext commandContext = new CommandContext();
            commandContext.setKafkaTopic(generatingRandomAlphanumericString());
            commandContext.setKafkaOffset(new Random().nextLong());
            commandContext.setKafkaPartition(new Random().nextInt());
            commands.add(commandContext);
        } while (times-- >= 0);
        return commands;
    }

    private String generatingRandomAlphanumericString() {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10_000;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
