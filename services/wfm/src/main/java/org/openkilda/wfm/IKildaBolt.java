package org.openkilda.wfm;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;

public interface IKildaBolt {
    TopologyContext getContext();
    OutputCollector getOutput();
}
