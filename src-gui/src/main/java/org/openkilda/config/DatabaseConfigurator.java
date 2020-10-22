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

import org.openkilda.dao.repository.VersionRepository;

import com.ibatis.common.jdbc.ScriptRunner;

import org.apache.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

@Repository("databaseConfigurator")
public class DatabaseConfigurator {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConfigurator.class);
    
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
        List<Long> versionNumberList = versionRepository.findAllVersionNumber();
        InputStream inputStream = null;
        try {
            File scriptFolder = resourceLoader.getResource("classpath:" + SCRIPT_LOCATION).getFile();
            String[] scriptFiles = scriptFolder.list();
            for (String scriptFile :  scriptFiles) {
                String scriptFileName = scriptFile.replaceFirst("[.][^.]+$", "");
                String[] scriptNumber = scriptFileName.split("_");
                Long scriptVersionNumber = Long.valueOf(scriptNumber[1]);
                inputStream = resourceLoader.getResource("classpath:" + SCRIPT_LOCATION + "/" 
                        + SCRIPT_FILE_PREFIX + scriptVersionNumber + SCRIPT_FILE_SUFFIX).getInputStream();
                if (inputStream != null) {
                    if (!versionNumberList.isEmpty()) {
                        if (!versionNumberList.contains(scriptVersionNumber)) {
                            runScript(inputStream);
                        }
                    } else {
                        runScript(inputStream);
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.error("Failed to load db scripts" + ex);
        }
    }

    private void runScript(final InputStream inputStream) {
        try (Connection con = dataSource.getConnection()) {
            //con.setAutoCommit(false);
            ScriptRunner sr = new ScriptRunner(con, false, false);
            sr.runScript(new InputStreamReader(inputStream));
        } catch (Exception e) {
            LOGGER.error("Error occurred while executing script", e);
        }
    }
}
