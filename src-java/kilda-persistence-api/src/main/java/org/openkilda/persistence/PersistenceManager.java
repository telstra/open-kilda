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

package org.openkilda.persistence;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.repositories.Repository;
import org.openkilda.persistence.repositories.RepositoryAreaBinding;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.RepositoryFactoryProxy;
import org.openkilda.persistence.spi.PersistenceImplementationFactory;
import org.openkilda.persistence.tx.TransactionManager;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A manager of persistence related APIs.
 * <p/>
 * The implementation must be serializable, see {@link Serializable}.
 */
public class PersistenceManager implements Serializable {
    private final PersistenceConfig persistenceConfig;

    private final PersistenceLayout layout;

    private final Map<PersistenceImplementationType, PersistenceImplementation> implementationByType = new HashMap<>();
    private final Map<Class<? extends PersistenceImplementation>, PersistenceImplementation>
            implementationByClass = new HashMap<>();

    public PersistenceManager(ConfigurationProvider configurationProvider) {
        this(configurationProvider, Collections.emptyMap());
    }

    public PersistenceManager(
            ConfigurationProvider configurationProvider,
            Map<PersistenceImplementationType, PersistenceImplementation> implementationOverlay) {
        persistenceConfig = configurationProvider.getConfiguration(PersistenceConfig.class);

        layout = newPersistenceLayout(persistenceConfig);
        for (PersistenceImplementationType entry : layout.getAllImplementations()) {
            PersistenceImplementation impl = implementationOverlay.get(entry);
            if (impl == null) {
                impl = newPersistenceImplementation(configurationProvider, entry);
            }
            implementationByType.put(entry, impl);
            implementationByClass.put(impl.getClass(), impl);
        }
    }

    // TODO(surabujin): temporary solution, query by set of repositories must be used instead
    public TransactionManager getTransactionManager() {
        PersistenceImplementation implementation = getImplementation(layout.getDefault());
        return newTransactionManager(implementation);
    }

    public TransactionManager getTransactionManager(PersistenceImplementationType implementationType) {
        PersistenceImplementation implementation = getImplementation(implementationType);
        return newTransactionManager(implementation);
    }

    /**
     * Create {@link TransactionManager} instance for specific implementation. Passed repositories set are mapped
     * into {@link PersistenceArea} and using {@link PersistenceLayout} into specific {@link PersistenceImplementation}.
     * All passed repositories must be mapped into one implementation.
     */
    public TransactionManager getTransactionManager(Repository<?> first, Repository<?>... extra) {
        PersistenceImplementationType firstType = layout.get(RepositoryAreaBinding.INSTANCE.lookup(first));

        Set<PersistenceImplementationType> targets = new HashSet<>();
        targets.add(firstType);
        for (Repository<?> entry : extra) {
            targets.add(layout.get(RepositoryAreaBinding.INSTANCE.lookup(entry)));
        }
        if (1 < targets.size()) {
            throw new IllegalArgumentException(String.format(
                    "Attempt to create transaction manager for multiple persistence implementation: %s",
                    targets.stream()
                            .map(Enum::name)
                            .sorted()
                            .collect(Collectors.joining(", "))));
        }

        return newTransactionManager(getImplementation(firstType));
    }

    public RepositoryFactory getRepositoryFactory() {
        return new RepositoryFactoryProxy(this);
    }

    /**
     * Get implementations instance by class reference.
     */
    public <T extends PersistenceImplementation> T getImplementation(Class<T> type) {
        PersistenceImplementation implementation = implementationByClass.get(type);
        if (implementation == null) {
            throw new IllegalArgumentException(String.format(
                    "%s persistence implementation is missing into %s", type.getName(), this));
        }
        return type.cast(implementation);
    }

    public PersistenceImplementation getImplementation(PersistenceArea area) {
        return getImplementation(layout.get(area), area.name());
    }

    public PersistenceImplementation getImplementation(PersistenceImplementationType type) {
        return getImplementation(type, "<default>");
    }

    private PersistenceImplementation getImplementation(PersistenceImplementationType type, String areaName) {
        PersistenceImplementation implementation = implementationByType.get(type);
        if (implementation == null) {
            throw new IllegalArgumentException(String.format(
                    "Persistence implementation %s (required by area %s) is missing "
                            + "(exists implementations: \"%s\")",
                    type, areaName,
                    implementationByType.keySet().stream().map(Enum::name).collect(Collectors.joining(", "))));
        }
        return implementation;
    }

    public void install() {
        PersistenceContextManager.install(this);
    }

    private TransactionManager newTransactionManager(PersistenceImplementation implementation) {
        return new TransactionManager(
                implementation,
                persistenceConfig.getTransactionRetriesLimit(), persistenceConfig.getTransactionRetriesMaxDelay());
    }

    private static PersistenceLayout newPersistenceLayout(PersistenceConfig config) {
        PersistenceLayout layout = new PersistenceLayout(config.getDefaultImplementationName());
        layout.add(PersistenceArea.COMMON, config.getCommonAreaImplementationName());
        layout.add(PersistenceArea.HISTORY, config.getHistoryAreaImplementationName());
        return layout;
    }

    private static PersistenceImplementation newPersistenceImplementation(
            ConfigurationProvider configurationProvider, PersistenceImplementationType implementation) {
        PersistenceImplementationFactory factory = loadPersistenceImplementationFactory(implementation);
        return factory.produce(configurationProvider);
    }

    private static PersistenceImplementationFactory loadPersistenceImplementationFactory(
            PersistenceImplementationType target) {
        ServiceLoader<PersistenceImplementationFactory> loader = ServiceLoader.load(
                PersistenceImplementationFactory.class);

        Set<PersistenceImplementationType> seen = new HashSet<>();
        PersistenceImplementationFactory needle = null;
        for (PersistenceImplementationFactory entry : loader) {
            PersistenceImplementationType current = entry.getType();
            seen.add(current);
            if (target == current) {
                if (needle != null) {
                    throw new IllegalStateException(String.format(
                            "Locate more than 1 persistence provider implementation tagged as \"%s\"", target));
                }
                needle = entry;
            }
        }

        if (needle == null) {
            throw new IllegalArgumentException(
                    String.format("No implementation of %s tagged with %s have been found "
                                    + "(seen implementation tags: \"%s\").",
                            PersistenceImplementationFactory.class.getName(), target,
                            seen.stream().map(Enum::name).collect(Collectors.joining("\", \""))));
        }

        return needle;
    }
}
