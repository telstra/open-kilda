package org.bitbucket.openkilda.floodlight.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bitbucket.openkilda.floodlight.kafka.KafkaMessageCollector;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by atopilin on 11/04/2017.
 */
public class InstallFlowTest {
    private ExecutorService parseRecordExecutor;

    @Before
    public void setUp() {
        parseRecordExecutor = Executors.newSingleThreadExecutor();
    }

    @Test
    public void installTransitFlow() {
        /*
        ConsumerRecord<String, String> record = new ConsumerRecord<String, String>();
        KafkaMessageCollector collector = new KafkaMessageCollector();
        KafkaMessageCollector.ParseRecord parseRecord = collector.new ParseRecord(record);
        parseRecordExecutor.execute(parseRecord);
        */
    }
}
