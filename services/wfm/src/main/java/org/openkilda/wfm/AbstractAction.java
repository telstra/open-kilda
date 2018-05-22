package org.openkilda.wfm;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.tuple.Tuple;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.wfm.error.UnsupportedActionException;
import org.openkilda.wfm.topology.stats.StatsTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAction implements Runnable {
    private final Logger logger;
    private final IKildaBolt bolt;
    private final Tuple tuple;

    public AbstractAction(IKildaBolt bolt, Tuple tuple) {
        this.logger = LoggerFactory.getLogger(StatsTopology.class);

        this.bolt = bolt;
        this.tuple = tuple;
    }

    @Override
    public void run() {
        try {
            handle();
        } catch (Exception e) {
            if (!handleError(e)) {
                rollback();
                return;
            }
        }
        commit();
    }

    protected abstract void handle()
            throws MessageFormatException, UnsupportedActionException, JsonProcessingException;

    protected Boolean handleError(Exception e) {
        getLogger().error("Unhandled exception", e);
        return false;
    }

    protected void commit() {
        getOutputCollector().ack(tuple);
    }

    protected void rollback() {
        getOutputCollector().fail(tuple);
    }

    public IKildaBolt getBolt() {
        return bolt;
    }

    public Tuple getTuple() {
        return tuple;
    }

    protected OutputCollector getOutputCollector() {
        return bolt.getOutput();
    }

    protected Logger getLogger() {
        return logger;
    }
}
