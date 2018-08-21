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

package org.openkilda.wfm.topology.utils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * This Bolt can be used to write messages to disk.
 */
public class FilerBolt extends BaseRichBolt {

    private static Logger logger = LoggerFactory.getLogger(FilerBolt.class);
    public File dir = Files.createTempDir();
    public String fileName = "utils.log";
    private OutputCollector outputCollector;
    private File file;

    public FilerBolt withDir(File dir) {
        this.dir = dir;
        return this;
    }

    public FilerBolt withFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public File getFile() {
        if (file == null) {
            dir.mkdirs();
            file = new File(dir.getAbsolutePath(), fileName);
        }
        return file;
    }

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        outputCollector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        File file = getFile();
        logger.debug("FILER: Writing tuple to disk: File = {}, tuple={}", file.getAbsolutePath(), tuple);

        try {
            // Start with just the values; determine later if the fields are needed.
            //Files.append(tuple.getFields().toString(), file, Charsets.UTF_8);
            Files.append(tuple.getValues().toString() + "\n", file, Charsets.UTF_8);
        } catch (IOException e) {
            logger.error("FILER: couldn't append to file: {}. Exception: {}. Cause: {}",
                    file.getAbsolutePath(), e.getMessage(), e.getCause());
        }
        outputCollector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }

}
