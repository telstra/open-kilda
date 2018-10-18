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

import static java.lang.String.format;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.neo4j.ogm.typeconversion.AttributeConverter;
import org.neo4j.ogm.typeconversion.ConversionCallback;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ConversionCallback} that looks up for converters in provided packages to perform
 * conversion.
 */
class SimpleConversionCallback implements ConversionCallback {
    private final Map<ParameterizedType, Class<? extends AttributeConverter>> converters;

    @SuppressWarnings("unchecked")
    SimpleConversionCallback(String... packages) {
        // Scan the packages for the converters.
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().whitelistPackages(packages).scan()) {
            ClassInfoList controlClasses = scanResult.getClassesImplementing(AttributeConverter.class.getName());
            List<Class<?>> controlClassRefs = controlClasses.loadClasses();

            this.converters = controlClassRefs.stream()
                    .map(clazz -> (Class<AttributeConverter>) clazz)
                    .collect(Collectors.toMap(
                            converter -> (ParameterizedType) Arrays.stream(converter.getGenericInterfaces())
                                    .filter(type -> type instanceof ParameterizedType)
                                    .filter(type ->
                                            ((ParameterizedType) type).getRawType().equals(AttributeConverter.class))
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalArgumentException(
                                            format("The converter %s must implement AttributeConverter interface",
                                                    converter.getSimpleName()))),
                            converter -> converter
                    ));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T convert(Class<T> targetType, Object value) {
        if (value == null) {
            return null;
        }

        // Look up for a converter with corresponding entity and graph classes.
        for (Map.Entry<ParameterizedType, Class<? extends AttributeConverter>> converter : converters.entrySet()) {
            Type[] genericTypes = converter.getKey().getActualTypeArguments();
            Class entityClass = (Class) genericTypes[0];
            Class graphClass = (Class) genericTypes[1];

            if (targetType.isAssignableFrom(entityClass) && graphClass.isAssignableFrom(value.getClass())) {
                try {
                    return (T) converter.getValue().newInstance().toEntityAttribute(value);
                } catch (InstantiationException | IllegalAccessException ex) {
                    throw new PersistenceException("Unable to instaniate the converter "
                            + converter.getValue().getSimpleName(), ex);
                }
            }

            if (targetType.isAssignableFrom(graphClass) && entityClass.isAssignableFrom(value.getClass())) {
                try {
                    return (T) converter.getValue().newInstance().toGraphProperty(value);
                } catch (InstantiationException | IllegalAccessException ex) {
                    throw new PersistenceException("Unable to instaniate the converter "
                            + converter.getValue().getSimpleName(), ex);
                }
            }
        }

        throw new PersistenceException("Unable to locate appropriate converter for " + targetType.getSimpleName());
    }
}
