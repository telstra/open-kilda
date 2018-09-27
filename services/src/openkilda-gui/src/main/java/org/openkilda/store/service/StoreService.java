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

import org.openkilda.store.auth.constants.AuthType;
import org.openkilda.store.auth.dao.entity.OauthConfigEntity;
import org.openkilda.store.auth.dao.repository.OauthConfigRepository;
import org.openkilda.store.common.constants.StoreType;
import org.openkilda.store.linkstore.dao.entity.LinkStoreRequestUrlsEntity;
import org.openkilda.store.linkstore.dao.repository.LinkStoreRequestUrlsRepository;
import org.openkilda.store.model.LinkStoreConfigDto;
import org.openkilda.store.model.StoreTypeDto;
import org.openkilda.store.model.UrlDto;
import org.openkilda.store.service.converter.OauthConfigConverter;
import org.openkilda.store.service.converter.StoreTypeConverter;
import org.openkilda.store.service.converter.UrlConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Class StoreService.
 */
@Service
public class StoreService {

    @Autowired
    LinkStoreRequestUrlsRepository linkStoreRequestUrlsRepository;

    @Autowired
    OauthConfigRepository oauthConfigRepository;

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
        UrlConverter urlConverter = new UrlConverter();
        List<LinkStoreRequestUrlsEntity>  linkStoreRequestUrlsEntitiesList = new ArrayList<LinkStoreRequestUrlsEntity>();
        List<LinkStoreRequestUrlsEntity>  linkStoreRequestUrlsEntities = linkStoreRequestUrlsRepository
                .findAll();
        for(Entry<String,UrlDto> urlEntrySet : linkStoreConfigDto.getUrls().entrySet()){
            if(linkStoreRequestUrlsEntities != null){
                LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntity = null;
                for(LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntityObj : linkStoreRequestUrlsEntities){
                    if(linkStoreRequestUrlsEntityObj.getUrlEntity().getName().equalsIgnoreCase(urlEntrySet.getValue().getName())){
                        linkStoreRequestUrlsEntity = linkStoreRequestUrlsEntityObj;
                        break;
                    }
                }
                if(linkStoreRequestUrlsEntity == null){
                    linkStoreRequestUrlsEntity = new LinkStoreRequestUrlsEntity();
                }
                linkStoreRequestUrlsEntity.setUrlEntity(urlConverter.toUrlEntity(urlEntrySet.getKey(), urlEntrySet.getValue(), linkStoreRequestUrlsEntity.getUrlEntity()));
                linkStoreRequestUrlsEntitiesList.add(linkStoreRequestUrlsEntity);
            }
        }
        linkStoreRequestUrlsEntitiesList = linkStoreRequestUrlsRepository.save(linkStoreRequestUrlsEntitiesList);

        Map<String, UrlDto> urls = new HashMap<String, UrlDto>();
        for (LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntity : linkStoreRequestUrlsEntitiesList) {
            urls.put(linkStoreRequestUrlsEntity.getUrlEntity().getName(),
                    urlConverter.toUrlDto(linkStoreRequestUrlsEntity.getUrlEntity()));
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
        UrlConverter urlConverter = new UrlConverter();
        List<LinkStoreRequestUrlsEntity>  linkStoreRequestUrlsEntitiesList = linkStoreRequestUrlsRepository
                .findAll();

        Map<String, UrlDto> urls = new HashMap<String, UrlDto>();
        for (LinkStoreRequestUrlsEntity linkStoreRequestUrlsEntity : linkStoreRequestUrlsEntitiesList) {
            urls.put(linkStoreRequestUrlsEntity.getUrlEntity().getName(),
                    urlConverter.toUrlDto(linkStoreRequestUrlsEntity.getUrlEntity()));
        }
        linkStoreConfigDto.setUrls(urls);
        return linkStoreConfigDto;
    }
}
