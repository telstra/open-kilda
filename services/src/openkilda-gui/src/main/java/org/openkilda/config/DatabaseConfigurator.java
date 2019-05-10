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

package org.openkilda.config;

import org.openkilda.dao.entity.VersionEntity;
import org.openkilda.dao.repository.VersionRepository;
import org.openkilda.service.FlowService;

import com.ibatis.common.jdbc.ScriptRunner;

import org.apache.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

@Repository("databaseConfigurator")
public class DatabaseConfigurator {

    private static final Logger LOGGER = Logger.getLogger(FlowService.class);
    
    private static final String SCRIPT_FILE_PREFIX = "import-script_";
    private static final String SCRIPT_FILE_SUFFIX = ".sql";
    private static final String SCRIPT_LOCATION = "db";
    
    private ResourceLoader resourceLoader;

    private VersionRepository versionRepository;

    private DataSource dataSource;

    public DatabaseConfigurator(@Autowired final VersionRepository versionRepository, final DataSource dataSource,
            final ResourceLoader resourceLoader) {
        this.versionRepository = versionRepository;
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        init();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void init() {
        loadInitialData();
    }

    private void loadInitialData() {
        List<VersionEntity> versionEntities = versionRepository.findAll();
        long lastestScriptNumber = getLatestScriptVersion(versionEntities);
        Map<String, InputStream> filesByName = getScriptFiles(lastestScriptNumber);

        for (int i = 0; i < filesByName.size(); i++) {
            try (InputStream inputStream = filesByName.get(lastestScriptNumber + i + 1)) {
                runScript(inputStream);
            } catch (IOException e) {
                LOGGER.error("Failed to load db script", e);
            }
        }
    }

    private Long getLatestScriptVersion(final List<VersionEntity> versionEntities) {
        Long lastestVersion = 0L;
        for (VersionEntity versionEntity : versionEntities) {
            if (lastestVersion < versionEntity.getVersionNumber()) {
                lastestVersion = versionEntity.getVersionNumber();
            }
        }
        return lastestVersion;
    }

    private Map<String, InputStream> getScriptFiles(final long scriptNumber) {
        long lastestScriptNumber = scriptNumber;
        Map<String, InputStream> filesByName = new LinkedHashMap<>();
        InputStream is;
        try {
            while (true) {
                lastestScriptNumber++;
                is = resourceLoader.getResource("classpath:" + SCRIPT_LOCATION + "/" + SCRIPT_FILE_PREFIX
                        + lastestScriptNumber + SCRIPT_FILE_SUFFIX).getInputStream();
                if (is != null) {
                    runScript(is);
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.info("Failed to load db scripts", e);
        }
        return filesByName;
    }

    private void runScript(final InputStream inputStream) {
        try (Connection con = dataSource.getConnection()) {
            ScriptRunner sr = new ScriptRunner(con, false, false);
            sr.runScript(new InputStreamReader(inputStream));
        } catch (Exception e) {
            LOGGER.error("Error occurred while executing script", e);
        }
    }
}
