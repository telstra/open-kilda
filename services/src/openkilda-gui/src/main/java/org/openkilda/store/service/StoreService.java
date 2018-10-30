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

package org.openkilda.store.service;

import org.openkilda.store.common.constants.StoreType;
import org.openkilda.store.common.constants.Url;
import org.openkilda.store.linkstore.dao.entity.LinkStoreRequestUrlsEntity;
import org.openkilda.store.linkstore.dao.repository.LinkStoreRequestUrlsRepository;
import org.openkilda.store.model.LinkStoreConfigDto;
import org.openkilda.store.model.StoreTypeDto;
import org.openkilda.store.model.UrlDto;
import org.openkilda.store.service.converter.StoreTypeConverter;
import org.openkilda.store.service.converter.UrlConverter;
import org.openkilda.utility.CollectionUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The Class StoreService.
 */
@Service
public class StoreService {

    @Autowired
    private LinkStoreRequestUrlsRepository linkStoreRequestUrlsRepository;

    /**
     * Gets the store types.
     *
     * @return the store types
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public List<StoreTypeDto> getStoreTypes() {
        List<StoreTypeDto> list = new ArrayList<StoreTypeDto>();
        StoreType[] storeTypes = StoreType.values();
        for (StoreType storeType : storeTypes) {
            list.add(StoreTypeConverter.toStoreTypeDto(storeType));
        }
        return list;
    }

    /**
     * Save or update link store config.
     *
     * @param linkStoreConfigDto the link store config dto
     * @return the link store config dto
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public LinkStoreConfigDto saveOrUpdateLinkStoreConfig(final LinkStoreConfigDto linkStoreConfigDto) {
        List<LinkStoreRequestUrlsEntity> linkStoreRequestUrlsEntitiesList = new ArrayList<LinkStoreRequestUrlsEntity>();
        List<LinkStoreRequestUrlsEntity> linkStoreRequestUrlsEntities = linkStoreRequestUrlsRepository.findAll();
        for (Entry<String, UrlDto> urlEntrySet : linkStoreConfigDto.getUrls().entrySet()) {
            if (linkStoreRequestUrlsEntities != null) {
                LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntity = null;
                for (LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntityObj : linkStoreRequestUrlsEntities) {
                    if (linkStoreRequestUrlsEntityObj.getUrlEntity().getName()
                            .equalsIgnoreCase(urlEntrySet.getValue().getName())) {
                        linkStoreRequestUrlsEntity = linkStoreRequestUrlsEntityObj;
                        break;
                    }
                }
                if (linkStoreRequestUrlsEntity == null) {
                    linkStoreRequestUrlsEntity = new LinkStoreRequestUrlsEntity();
                }
                linkStoreRequestUrlsEntity.setUrlEntity(UrlConverter.toUrlEntity(urlEntrySet.getKey(),
                        urlEntrySet.getValue(), linkStoreRequestUrlsEntity.getUrlEntity()));
                linkStoreRequestUrlsEntitiesList.add(linkStoreRequestUrlsEntity);
            }
        }
        linkStoreRequestUrlsEntitiesList = linkStoreRequestUrlsRepository.save(linkStoreRequestUrlsEntitiesList);

        Map<String, UrlDto> urls = new HashMap<String, UrlDto>();
        for (LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntity : linkStoreRequestUrlsEntitiesList) {
            urls.put(linkStoreRequestUrlsEntity.getUrlEntity().getName(),
                    UrlConverter.toUrlDto(linkStoreRequestUrlsEntity.getUrlEntity()));
        }
        linkStoreConfigDto.setUrls(urls);
        return linkStoreConfigDto;
    }

    /**
     * Gets the link store config.
     *
     * @return the link store config
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public LinkStoreConfigDto getLinkStoreConfig() {
        LinkStoreConfigDto linkStoreConfigDto = new LinkStoreConfigDto();
        List<LinkStoreRequestUrlsEntity> linkStoreRequestUrlsEntitiesList = linkStoreRequestUrlsRepository.findAll();

        Map<String, UrlDto> urls = new HashMap<String, UrlDto>();
        for (LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntity : linkStoreRequestUrlsEntitiesList) {
            urls.put(linkStoreRequestUrlsEntity.getUrlEntity().getName(),
                    UrlConverter.toUrlDto(linkStoreRequestUrlsEntity.getUrlEntity()));
        }
        linkStoreConfigDto.setUrls(urls);
        return linkStoreConfigDto;
    }

    /**
     * Gets the url.
     *
     * @param storeType the store type
     * @param url the url
     * @return the url
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public UrlDto getUrl(final StoreType storeType, final Url url) {
        UrlDto urlDto = null;
        
        if (storeType == StoreType.LINK_STORE) {
            LinkStoreRequestUrlsEntity urlsEntity = linkStoreRequestUrlsRepository
                    .findByUrlEntity_name(url.getName());
            urlDto = UrlConverter.toUrlDto(urlsEntity.getUrlEntity());
        }

        return urlDto;
    }
    
    /**
     * Delete link store config.
     *
     * @return true, if successful
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public boolean deleteLinkStoreConfig() {
        linkStoreRequestUrlsRepository.deleteAll();
        List<LinkStoreRequestUrlsEntity> linkStoreRequestUrlsEntitiesList = linkStoreRequestUrlsRepository.findAll();
        return CollectionUtil.isEmpty(linkStoreRequestUrlsEntitiesList);
    }

}
