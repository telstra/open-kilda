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

package org.openkilda.wfm;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Created by carmine on 4/3/17.
 */
public class WhateverTest {

    public static void main(String[] args) {
        List<String> results = new ArrayList<>();

        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "test");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "15000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        String topic = "speaker.info.switch.updown";
        consumer.subscribe(Arrays.asList(topic));
        //        System.out.println("partitions = " + consumer.partitionsFor(topic).size());
        //        PartitionInfo info = consumer.partitionsFor(topic).get(0);
        //        TopicPartition part = new TopicPartition(topic,info.partition());
        //        System.out.println("looking at " + info.topic() + " part " + info.partition());
        //        consumer.seekToBeginning(Arrays.asList(part));
        System.out.println("consumer.listTopics() = " + consumer.listTopics());

        System.out.println("");
        for (int i = 0; i < 10; i++) {
            ConsumerRecords<String, String> records = consumer.poll(500);
            System.out.println(".");
            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("offset = %d, key = %s, value = %s", record.offset(), record.key(), record.value());
                System.out.print("$");
                results.add(record.value());
            }
            Utils.sleep(1000);
        }
        System.out.println("");
        consumer.close();

    }
}
