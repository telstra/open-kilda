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

package org.openkilda;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class KafkaParameters {

    private Properties properties;

    public KafkaParameters() throws IOException {
        properties = new Properties();
        InputStream input = null;

        try {
            String filename = "atdd.properties";
            input = KafkaParameters.class.getClassLoader().getResourceAsStream(filename);
            if (input == null) {
                throw new IOException("Can't find " + filename);
            }
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public String getBootstrapServers() {
        return properties.getProperty("kafka.hosts");
    }

    public String getControlTopic() {
        return properties.getProperty("kafka.topic.ctrl");
    }

    public String getDiscoTopic() {
        return properties.getProperty("kafka.topic.topo.disco");
    }
}
