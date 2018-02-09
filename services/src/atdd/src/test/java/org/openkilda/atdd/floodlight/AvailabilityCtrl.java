package org.openkilda.atdd.floodlight;

import org.openkilda.KafkaUtils;
import org.openkilda.messaging.ctrl.KafkaBreakTarget;

import cucumber.api.PendingException;
import cucumber.api.java.en.When;

import java.io.IOException;

public class AvailabilityCtrl {
    KafkaBreaker kafkaBreaker;

    public AvailabilityCtrl() throws IOException {
        KafkaUtils kafkaUtils = new KafkaUtils();
        kafkaBreaker = new KafkaBreaker(kafkaUtils.getConnectDefaults());
    }

    @When("^link between controller and kafka are lost$")
    public void link_between_controller_and_kafka_are_lost() throws Throwable {
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_PRODUCER);
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_CONSUMER);
    }

    @When("^link between controller and kafka restored$")
    public void link_between_controller_and_kafka_restored() throws Throwable {
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_PRODUCER);
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_CONSUMER);
    }

    @When("^link between all switches and controller are lost$")
    public void link_between_all_switches_and_controller_are_lost() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }

    @When("^link between all switches and controller restored$")
    public void link_between_all_switches_and_controller_restored() throws Throwable {
        // Write code here that turns the phrase above into concrete actions
        throw new PendingException();
    }
}
