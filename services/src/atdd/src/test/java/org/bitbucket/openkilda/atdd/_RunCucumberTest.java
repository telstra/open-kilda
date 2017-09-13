package org.bitbucket.openkilda.atdd;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;

/*
 * The features option allows us to filter which directories to look at for features.
 * The tags option allows us to select the scenarios within those features.
 *
 * At present, with an mvp1 directory and an mvp1 tag, one of these mechanisms isn't needed.
 * However, we'll most likely merge the features back into a single directory, or follow some other
 * structure.
 */
@RunWith(Cucumber.class)
@CucumberOptions(
      plugin = { "pretty", "html:target/cucumber" }
        , features = {"src/test/resources/features/mvp.1","src/test/resources/features/mvp.tbd"}
        , tags = {"@MVP1"}
    )
public class _RunCucumberTest {
}
