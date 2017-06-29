package org.openkilda;

import cucumber.api.PendingException;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class RefundFeatureTest {

	public RefundFeatureTest() {
	}

	@Given("^Jeff has bought a microwave for \\$(\\d+)$")
	public void jeff_has_bought_a_microwave_for_$(int arg1) throws Throwable {
		// Write code here that turns the phrase above into concrete actions
		// throw new PendingException();
		assert true;
	}

	@Given("^he has a receipt$")
	public void he_has_a_receipt() throws Throwable {
		// Write code here that turns the phrase above into concrete actions
		throw new PendingException();
	}

	@When("^he returns the microwave$")
	public void he_returns_the_microwave() throws Throwable {
		// Write code here that turns the phrase above into concrete actions
		throw new PendingException();
	}

	@Then("^Jeff should be refunded \\$(\\d+)$")
	public void jeff_should_be_refunded_$(int arg1) throws Throwable {
		// Write code here that turns the phrase above into concrete actions
		throw new PendingException();
	}
}
