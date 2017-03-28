package org.bitbucket.openkilda.wfm;

import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;

import java.util.Map;
import java.util.Random;

//This spout randomly emits sentences
public class RandomSentenceSpout extends BaseRichSpout {
    //Collector used to emit output
    SpoutOutputCollector _collector;
    //Used to generate a random number
    Random _rand;

    //Open is called when an instance of the class is created
    @Override
    public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
        //Set the instance collector to the one passed in
        _collector = collector;
        //For randomness
        _rand = new Random();
    }

    //Emit data to the stream
    @Override
    public void nextTuple() {
        //Sleep for a bit
        Utils.sleep(100);
        //The sentences that are randomly emitted
        String[] sentences = new String[]{ "the cow jumped over the moon", "an apple a day keeps the doctor away",
                "four score and seven years ago", "snow white and the seven dwarfs", "i am at two with nature" };
        //Randomly pick a sentence
        String sentence = sentences[_rand.nextInt(sentences.length)];
        //Emit the sentence
        _collector.emit(new Values(sentence));
    }

    //Ack is not implemented since this is a basic example
    @Override
    public void ack(Object id) {
    }

    //Fail is not implemented since this is a basic example
    @Override
    public void fail(Object id) {
    }

    //Declare the output fields. In this case, an sentence
    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("sentence"));
    }
}
