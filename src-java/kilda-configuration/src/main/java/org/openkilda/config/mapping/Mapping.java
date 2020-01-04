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

package org.openkilda.config.mapping;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.openkilda.config.mapping.Mapping.MAPPING_META_ATTR;

import com.sabre.oss.conf4j.annotation.Meta;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This is a markup annotation which indicates whether mapping it to be applied.
 * <p/>
 * Mapping is useful when there is a need to map or decorate a configuration value based on additional rules.
 * <p/>
 * <b>Note:</b> This annotation can be used both at the class and at the method level.
 * <p/>
 * <b>Usage example:</b>
 * <pre>
 * &#064;Configuration
 * &#064;Mapping(target = "KAFKA_TOPIC")
 * public interface KafkaConfiguration {
 *    &#064;Key
 *    String getIncomingTopic();
 * }
 *
 * &#064;Configuration
 * public interface KafkaConfiguration {
 *    &#064;Key
 *    &#064;Mapping(target = "KAFKA_TOPIC")
 *    String getOutgoingTopic();
 * }
 * </pre>
 */
@Meta(name = MAPPING_META_ATTR)
@Retention(RUNTIME)
@Target({TYPE, METHOD})
@Documented
public @interface Mapping {
    String MAPPING_META_ATTR = "mapping";

    /**
     * The target defines what rules is to be used for mapping.
     */
    String target();
}
