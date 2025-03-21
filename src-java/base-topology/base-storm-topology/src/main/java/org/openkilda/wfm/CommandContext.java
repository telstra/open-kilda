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

package org.openkilda.wfm;

import org.openkilda.messaging.Message;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.BeanSerializer;
import lombok.Data;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.io.Serializable;
import java.util.UUID;

/**
 * Class that contains command context information.
 */
@Data
@DefaultSerializer(BeanSerializer.class)
public class CommandContext implements Serializable {
    private final String correlationId;
    private final long createTime;

    private String kafkaTopic = null;
    private Integer kafkaPartition = null;
    private Long kafkaOffset = null;

    public CommandContext() {
        this(UUID.randomUUID().toString(), null);
    }

    public CommandContext(ConsumerRecord<?, ?> kafkaRecord) {
        this(UUID.randomUUID().toString(), kafkaRecord);
    }

    public CommandContext(Message message) {
        this(message.getCorrelationId(), message.getTimestamp(), null);
    }

    public CommandContext(Message message, ConsumerRecord<?, ?> kafkaRecord) {
        this(message.getCorrelationId(), message.getTimestamp(), kafkaRecord);
    }

    public CommandContext(String correlationId) {
        this(correlationId, System.currentTimeMillis(), null);
    }

    public CommandContext(String correlationId, ConsumerRecord<?, ?> kafkaRecord) {
        this(correlationId, kafkaRecord != null ? kafkaRecord.timestamp() : System.currentTimeMillis(), kafkaRecord);
    }

    protected CommandContext(String correlationId, long createTime, ConsumerRecord<?, ?> kafkaRecord) {
        this.correlationId = correlationId;
        this.createTime = createTime;

        if (kafkaRecord != null) {
            kafkaTopic = kafkaRecord.topic();
            kafkaPartition = kafkaRecord.partition();
            kafkaOffset = kafkaRecord.offset();
        }
    }

    /**
     * Creates a new {@link CommandContext} object using data from the current one. This produces an object with
     * extended correlation ID: the original correlation ID followed by a colon and the correlationIdExtension.
     * This is used to chain different commands.
     * @param correlationIdExtension additional correlation ID
     * @return a composite CommandContext
     */
    public CommandContext fork(String correlationIdExtension) {
        CommandContext nested = new CommandContext(correlationId + " : " + correlationIdExtension);
        nested.merge(this);
        return nested;
    }

    /**
     * Merge data from other CommandContext object.
     *
     * <p>Become useful when part of processing is done in external "branch" and separate CommandContext was
     * created.
     */
    public void merge(CommandContext other) { }
}
