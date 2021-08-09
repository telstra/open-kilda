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

package org.openkilda.persistence;

import org.openkilda.persistence.context.PersistenceContextManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DummyPersistenceContextManager implements PersistenceContextManager {
    private boolean isActive = false;

    @Override
    public void initContext() {
        log.debug("{}.initContext() have been called", getClass().getName());
        isActive = true;
    }

    @Override
    public void closeContext() {
        log.debug("{}.closeContext() have been called", getClass().getName());
        isActive = false;
    }

    @Override
    public boolean isContextInitialized() {
        log.debug("{}.isContextInitialized() have been called", getClass().getName());
        return isActive;
    }

    @Override
    public boolean isTxOpen() {
        throw new IllegalStateException(String.format("%s.isTxOpen() have been called", getClass().getName()));
    }
}
