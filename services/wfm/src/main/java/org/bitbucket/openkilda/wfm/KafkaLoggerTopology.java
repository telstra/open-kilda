package org.bitbucket.openkilda.wfm;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.logging.log4j.Level;

/**
 * A topology that will listen to a kafka topic and log the messages at the configured level.
 *
 * NB: This will only be able to log if the configured log level is below what is passed in.
 *      Consequently, the default log level is INFO, which is the default configured setting.
 *      In other words, the default loglevel is INFO. If KafkaLoggerTopology is configured with
 *      DEBUG, then the messages won't appear.
 *
 *      The alternative is to adjust the loglevel of KafkaLoggerTopology to that of what is
 *      configured, but that doesn't feel right. Let's see how this works in operations.
 *
 * Example Call:
 *  (1) storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *  org.bitbucket.openkilda.wfm.KafkaLoggerTopology logger-5 kilda.speaker INFO fred
 *  (2) ==> using a FQDN zookeeper <==
 *  storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *  org.bitbucket.openkilda.wfm.KafkaLoggerTopology logger-5 kilda.speaker INFO fred \
 *  zookeeper.pendev:2181
 *  (3) ==> using a localhost zookeeper <==
 *  storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *  org.bitbucket.openkilda.wfm.KafkaLoggerTopology logger-5 kilda.speaker INFO fred localhost:2181
 *
 */
public class KafkaLoggerTopology {

    /** assigned after createTopology() is called */
    public LoggerBolt logger;

    public StormTopology createTopology(String topic, Level level, String watermark,
                                               String zookeeper){
        TopologyBuilder builder = new TopologyBuilder();

        String spoutId = "KafkaSpout-" + topic;
        int parallelism = 1;
        KafkaUtils kutils = new KafkaUtils().withZookeeperHost(zookeeper);

        builder.setSpout(spoutId, kutils.createKafkaSpout(topic), parallelism);
        logger = new LoggerBolt().withLevel(level).withWatermark(watermark);

        builder.setBolt("Logger", logger, parallelism)
                .shuffleGrouping(spoutId);
        return builder.createTopology();
    }


    public static void main(String[] args) throws Exception {
        // process command line ... topoName topic level watermark zookeeper
        String topoName = (args != null && args.length > 0) ?
                args[0] : "kafka.inspector."+System.currentTimeMillis();
        String topic = (args != null && args.length > 1) ? args[1] : "kilda.speaker";
        Level level = (args != null && args.length > 2) ? Level.valueOf(args[2]) : Level.INFO;
        String watermark = (args != null && args.length > 3) ? args[3] : "";
        String zookeeper = (args != null && args.length > 4) ? args[4] : "zookeeper.pendev:2181";
        boolean debug = (level == Level.DEBUG || level == Level.TRACE || level == Level.ALL);

        Config conf = new Config();
        conf.setDebug(debug);
        conf.setNumWorkers(1);
        StormSubmitter.submitTopology(topoName, conf,
               new KafkaLoggerTopology().createTopology(topic, level, watermark,zookeeper));
    }

}
