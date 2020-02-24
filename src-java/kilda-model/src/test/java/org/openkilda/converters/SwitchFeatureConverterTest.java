/* Copyright 2019 Telstra Open Source
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

package org.openkilda.converters;

import org.openkilda.model.SwitchFeature;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SwitchFeatureConverterTest {
    private static SwitchFeatureConverter converter;

    @BeforeClass
    public static void setupOnce() {
        converter = new SwitchFeatureConverter();
    }

    @Test
    public void toGraphPropertyTest() {
        Assert.assertEquals(getFeaturesAsStringSet(),
                converter.toGraphProperty(new HashSet<>(Arrays.asList(SwitchFeature.values()))));
    }

    @Test
    public void toEntityAttributeTest() {
        Assert.assertEquals(new HashSet<>(Arrays.asList(SwitchFeature.values())),
                converter.toEntityAttribute(getFeaturesAsStringSet()));
    }

    private Set<String> getFeaturesAsStringSet() {
        return Arrays.stream(SwitchFeature.values())
                .map(SwitchFeature::toString)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
