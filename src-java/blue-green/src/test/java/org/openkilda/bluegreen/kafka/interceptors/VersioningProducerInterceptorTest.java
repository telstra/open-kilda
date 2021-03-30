/* Copyright 2020 Telstra Open Source
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

package org.openkilda.bluegreen.kafka.interceptors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openkilda.bluegreen.kafka.Utils.MESSAGE_VERSION_HEADER;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_COMPONENT_NAME_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_RUN_ID_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_ZOOKEEPER_CONNECTION_STRING_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_ZOOKEEPER_RECONNECTION_DELAY_PROPERTY;

import org.openkilda.bluegreen.ZkWatchDog;

import com.google.common.collect.Lists;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VersioningProducerInterceptorTest {

    public static final String TOPIC = "topic";
    public static final String VALUE = "value";
    public static final String VERSION_1 = "version1";
    public static final String VERSION_2 = "version2";

    @Test
    public void nullVersionTest() {
        VersioningProducerInterceptor<String, String> interceptor = createInterceptor();
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, VALUE);
        record.headers().add(MESSAGE_VERSION_HEADER, VERSION_1.getBytes());
        ProducerRecord<String, String> result = interceptor.onSend(record);

        // version inside interceptor is not set. Interceptor must not add version header and remove old version headers
        Assert.assertFalse(result.headers().headers(MESSAGE_VERSION_HEADER).iterator().hasNext());
    }

    @Test
    public void validVersionTest() {
        VersioningProducerInterceptor<String, String> interceptor = createInterceptor();
        interceptor.handle(VERSION_1);

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, VALUE);
        ProducerRecord<String, String> result = interceptor.onSend(record);

        List<Header> versionHeaders = Lists.newArrayList(result.headers().headers(MESSAGE_VERSION_HEADER));
        Assert.assertEquals(1, versionHeaders.size());
        Assert.assertEquals(VERSION_1, new String(versionHeaders.get(0).value()));
    }

    @Test
    public void overrideVersionTest() {
        VersioningProducerInterceptor<String, String> interceptor = createInterceptor();
        interceptor.handle(VERSION_2);

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, VALUE);
        record.headers().add(MESSAGE_VERSION_HEADER, VERSION_1.getBytes());

        ProducerRecord<String, String> result = interceptor.onSend(record);

        // record had version1 header, but it was replaced with version2
        List<Header> versionHeaders = Lists.newArrayList(result.headers().headers(MESSAGE_VERSION_HEADER));
        Assert.assertEquals(1, versionHeaders.size());
        Assert.assertEquals(VERSION_2, new String(versionHeaders.get(0).value()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missedConnectionStringTest() {
        VersioningProducerInterceptor<String, String> interceptor = createInterceptor();
        interceptor.configure(new HashMap<>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void missedRunIdTest() {
        Map<String, String> config = new HashMap<>();
        config.put(PRODUCER_ZOOKEEPER_CONNECTION_STRING_PROPERTY, "test");

        VersioningProducerInterceptor<String, String> interceptor = createInterceptor();
        interceptor.configure(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missedComponentNameTest() {
        Map<String, String> config = new HashMap<>();
        config.put(PRODUCER_ZOOKEEPER_CONNECTION_STRING_PROPERTY, "test");
        config.put(PRODUCER_RUN_ID_PROPERTY, "run_id");

        VersioningProducerInterceptor<String, String> interceptor = createInterceptor();
        interceptor.configure(config);
    }

    @Test
    public void configureTest() {
        Map<String, String> config = new HashMap<>();
        config.put(PRODUCER_ZOOKEEPER_CONNECTION_STRING_PROPERTY, "test");
        config.put(PRODUCER_RUN_ID_PROPERTY, "run_id");
        config.put(PRODUCER_COMPONENT_NAME_PROPERTY, "name");
        config.put(PRODUCER_ZOOKEEPER_RECONNECTION_DELAY_PROPERTY, "100");

        VersioningProducerInterceptor<String, String> interceptor = Mockito.mock(VersioningProducerInterceptor.class);
        doCallRealMethod().when(interceptor).configure(any());

        interceptor.configure(config);
        verify(interceptor, times(1)).initWatchDog();
    }

    private VersioningProducerInterceptor<String, String> createInterceptor() {
        ZkWatchDog watchDog = Mockito.mock(ZkWatchDog.class);
        when(watchDog.isConnectedAndValidated()).thenReturn(true);

        VersioningProducerInterceptor<String, String> interceptor = new VersioningProducerInterceptor<>();
        interceptor.watchDog = watchDog;
        return interceptor;
    }
}
