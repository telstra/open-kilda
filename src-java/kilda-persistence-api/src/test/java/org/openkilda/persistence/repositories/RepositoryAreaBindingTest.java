/* Copyright 2021 Telstra Open Source
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

package org.openkilda.persistence.repositories;

import org.openkilda.model.Speaker;
import org.openkilda.persistence.PersistenceArea;

import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.util.Optional;

public class RepositoryAreaBindingTest {
    @Test
    public void testLookup() {
        Assert.assertEquals(PersistenceArea.COMMON, RepositoryAreaBinding.INSTANCE.lookup(SpeakerRepository.class));
        Assert.assertEquals(
                PersistenceArea.COMMON, RepositoryAreaBinding.INSTANCE.lookup(DummySpeakerRepository.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLookupFailure() {
        RepositoryAreaBinding.INSTANCE.lookup(DummyRepositoryBase.class);
    }

    private static class DummyRepositoryBase implements Serializable {
        // required only to emulate standard repository class hierarchy
    }

    private static class DummySpeakerRepository extends DummyRepositoryBase implements SpeakerRepository {
        @Override
        public void add(Speaker entity) {
            throw new IllegalStateException("Dummy method called");
        }

        @Override
        public void remove(Speaker entity) {
            throw new IllegalStateException("Dummy method called");
        }


        @Override
        public void detach(Speaker entity) {
            throw new IllegalStateException("Dummy method called");
        }

        @Override
        public Optional<Speaker> findByName(String name) {
            throw new IllegalStateException("Dummy method called");
        }
    }
}
