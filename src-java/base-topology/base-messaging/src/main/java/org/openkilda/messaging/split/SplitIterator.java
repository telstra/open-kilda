/* Copyright 2023 Telstra Open Source
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

package org.openkilda.messaging.split;

import lombok.Getter;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class helps to split an object on several chunks.
 *
 * @param <T> Class of an object which needs to be split.
 * @param <B> Class of a builder for an object which needs to be split.
 */
public class SplitIterator<T, B> {
    /**
     * Remaining free space in the current chunk.
     */
    @Getter
    private int remainingChunkSize;

    /**
     * Maximum size of one chunk.
     */
    private final int chunkSize;

    /**
     * Boolean value, which says if we need to add curren object to re result list.
     * It is used at the end of splitting to do not add empty objects to the end of a list.
     */
    @Getter
    private boolean addCurrent;

    /**
     * Function, which build an object of type T, from builder B. Usually it looks like:
     * <pre> builder::build </pre>
     */
    private final Function<B, T> buildFunction;

    /**
     * When a splitter creates a new chunk it needs to have some start object. This supplier creates initial builder for
     * such object. It can be just an empty builder, but sometimes all elements in a list must have one equal field
     * (for example switchId). In this case this supplier must return a builder with set switchId field.
     */
    private final Supplier<B> defaultBuilderSupplier;

    /**
     * Builder, which contains current version of an object. Size of the current object can't be greater than
     * `chunkSize`. When object size reaches `chunkSize` splitter returns it from `next()` method and creates a new
     * `currentBuilder` via `defaultBuilderSupplier`.
     */
    @Getter
    private B currentBuilder;

    public SplitIterator(
            int remainingChunkSize, int chunkSize, Function<B, T> buildFunction, Supplier<B> defaultBuilderSupplier) {
        this.chunkSize = chunkSize;
        this.remainingChunkSize = remainingChunkSize;
        this.addCurrent = true;
        this.buildFunction = buildFunction;
        this.defaultBuilderSupplier = defaultBuilderSupplier;
        this.currentBuilder = defaultBuilderSupplier.get();
    }

    /**
     * Updates size of the current chunk and returns current chunk if its size is equal to `chunkSize`.
     *
     * @param size size of a sub object which needs to be added into current chunk.
     * @return An `Optional` with object of type `T` if current chunk size is equal to `chunkSize`.
     *         Empty `Optional` otherwise.
     */
    public Optional<T> next(int size) {
        remainingChunkSize -= size;
        addCurrent = true;

        if (remainingChunkSize <= 0) {
            remainingChunkSize = chunkSize;
            addCurrent = false;
            T result = buildFunction.apply(currentBuilder);
            currentBuilder = defaultBuilderSupplier.get();
            return Optional.of(result);
        }
        return Optional.empty();
    }
}
