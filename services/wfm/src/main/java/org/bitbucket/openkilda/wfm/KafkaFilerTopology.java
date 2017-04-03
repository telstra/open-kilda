package org.bitbucket.openkilda.wfm;

import org.apache.logging.log4j.Level;
import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;

import java.io.File;

/**
 * Take a kafka topic and dump it to file.
 */
public class KafkaFilerTopology {

    /** assigned after createTopology() is called */
    public FilerBolt filer;

    public StormTopology createTopology(String topic, String dir, String zookeeper){
        TopologyBuilder builder = new TopologyBuilder();

        String spoutId = "KafkaSpout-" + topic;
        int parallelism = 1;
        KafkaUtils kutils = new KafkaUtils().withZookeeperHost(zookeeper);

        builder.setSpout(spoutId, kutils.createKafkaSpout(topic), parallelism);
        filer = new FilerBolt().withFileName("filer-"+topic+".log");
        if (dir != null && dir.length() > 0)
            filer.withDir(new File(dir));

        builder.setBolt("filer", filer, parallelism)
                .shuffleGrouping(spoutId);
        return builder.createTopology();
    }


    public static void main(String[] args) throws Exception {
        // process command line ... topoName topic level watermark zookeeper
        String topoName = (args != null && args.length > 0) ?
                args[0] : "kafka.inspector."+System.currentTimeMillis();
        String topic = (args != null && args.length > 1) ? args[1] : "kilda.speaker";
        String dir = (args != null && args.length > 2) ? args[2] : "";
        String zookeeper = (args != null && args.length > 3) ? args[3] : "zookeeper.pendev:2181";

        Config conf = new Config();
        conf.setDebug(false);
        conf.setNumWorkers(1);
        StormSubmitter.submitTopology(topoName, conf,
                new KafkaFilerTopology().createTopology(topic, dir, zookeeper));
    }

}
