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

package org.openkilda.messaging.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CacheTimeTag extends InfoData {

    @JsonProperty("created_in_cache")
    private Long createdInCache;

    @JsonProperty("updated_in_cache")
    private Long updatedInCache;

    public CacheTimeTag() {
    }

    @JsonCreator
    public CacheTimeTag(@JsonProperty("created_in_cache") Long createdInCache,
            @JsonProperty("updated_in_cache") Long updatedInCache) {
        this.createdInCache = createdInCache;
        this.updatedInCache = updatedInCache;
    }

    public Long getCreatedInCache() {
        return createdInCache;
    }

    public void setCreatedInCache(Long createdInCache) {
        this.createdInCache = createdInCache;
    }

    public void setCreatedInCacheNow() {
        this.createdInCache = getUnixTimestamp();
    }

    public Long getUpdatedInCache() {
        return updatedInCache;
    }

    public void setUpdatedInCache(Long updatedInCache) {
        this.updatedInCache = updatedInCache;
    }

    public void setUpdatedInCacheNow() {
        this.updatedInCache = getUnixTimestamp();
    }

    public void copyTimeTag(CacheTimeTag src) {
        this.createdInCache = src.createdInCache;
        this.updatedInCache = src.updatedInCache;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CacheTimeTag)) {
            return false;
        }
        CacheTimeTag that = (CacheTimeTag) o;
        return Objects.equal(createdInCache, that.createdInCache)
                && Objects.equal(updatedInCache, that.updatedInCache);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(createdInCache, updatedInCache);
    }


    private Long getUnixTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }
}
