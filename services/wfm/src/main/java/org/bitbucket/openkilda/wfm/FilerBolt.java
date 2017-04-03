package org.bitbucket.openkilda.wfm;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * This Bolt can be used to write messages to disk.
 */
public class FilerBolt extends BaseRichBolt {

    private OutputCollector _collector;
    private static Logger logger = LogManager.getLogger(FilerBolt.class);

    public File     dir = Files.createTempDir();
    public String   fileName = "filer.log";

    public FilerBolt withDir (File dir){
        this.dir = dir;
        return this;
    }

    public FilerBolt withFileName(String fileName){
        this.fileName = fileName;
        return this;
    }

    private File file;
    public File getFile(){
        if (file == null){
            dir.mkdirs();
            file = new File(dir.getAbsolutePath(),fileName);
        }
        return file;
    }

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        _collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        File file = getFile();
        logger.debug("FILER: Writing tuple to disk: File = {}", file.getAbsolutePath());

        if (file != null) {
            try {
                // Start with just the values; determine later if the fields are needed.
                //Files.append(tuple.getFields().toString(), file, Charsets.UTF_8);
                Files.append(tuple.getValues().toString()+"\n", file, Charsets.UTF_8);
            } catch (IOException e) {
                logger.error("FILER: couldn't append to file: {}. Exception: {}. Cause: {}",
                        file.getAbsolutePath(), e.getMessage(), e.getCause());
            }
        }
        _collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {}

}
