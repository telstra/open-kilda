package org.bitbucket.openkilda.atdd;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(
      plugin = { "pretty", "html:target/cucumber" }
    , features = "src/test/resources/features"
    )
public class _RunCucumberTest {
}
