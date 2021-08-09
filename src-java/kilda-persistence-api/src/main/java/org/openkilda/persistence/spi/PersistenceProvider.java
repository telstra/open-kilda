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

package org.openkilda.persistence.spi;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.persistence.PersistenceConfig;
import org.openkilda.persistence.PersistenceManager;

import java.io.Serializable;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * A provider for persistence manager(s). SPI is used to locate an implementation.
 *
 * @see ServiceLoader
 */
public final class PersistenceProvider implements Serializable {
    private static PersistenceManager OVERLAY;

    /**
     * Load {@link PersistenceManager} and make default i.e. used by aspects.
     */
    public static PersistenceManager loadAndMakeDefault(ConfigurationProvider configurationProvider) {
        return makeDefault(load(configurationProvider));
    }

    /**
     * Load and init a {@link PersistenceManager} instance using data provided in configuration to choose specific
     * implementation.
     */
    public static PersistenceManager load(ConfigurationProvider configurationProvider) {
        if (OVERLAY != null) {
            return OVERLAY;
        }

        PersistenceConfig persistenceConfig = configurationProvider.getConfiguration(PersistenceConfig.class);
        PersistenceManagerFactory factory = loadFactory(persistenceConfig.getImplementationName());

        return factory.produce(configurationProvider);
    }

    public static PersistenceManager makeDefault(PersistenceManager manager) {
        PersistenceManagerSupplier.DEFAULT.define(manager);
        return manager;
    }

    public static void setupLoadOverlay(PersistenceManager overlay) {
        OVERLAY = overlay;
    }

    public static void removeLoadOverlay() {
        OVERLAY = null;
    }

    private static PersistenceManagerFactory loadFactory(String implementationName) {
        ServiceLoader<PersistenceManagerFactory> loader = ServiceLoader.load(PersistenceManagerFactory.class);

        Set<String> seen = new HashSet<>();
        String normalizedName = implementationName.toLowerCase();
        PersistenceManagerFactory needle = null;
        for (PersistenceManagerFactory entry : loader) {
            String current = entry.getImplementationName().toLowerCase();
            seen.add(current);
            if (normalizedName.equals(current)) {
                if (needle != null) {
                    throw new IllegalStateException(String.format(
                            "Locate more than 1 persistence provider implementation names as \"%s\"",
                            implementationName));
                }
                needle = entry;
            }
        }
        if (needle == null) {
            throw new IllegalStateException(
                    String.format("No implementation of %s with implementation name %s have been found "
                                    + "(seen implementations: \"%s\").",
                            PersistenceManagerFactory.class.getName(), implementationName,
                            String.join("\", \"", seen)));
        }

        return needle;
    }

    private PersistenceProvider() {
        // do not allow to make any instances
    }
}
