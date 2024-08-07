package org.openkilda.performancetests.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan(basePackages = ["org.openkilda.functionaltests.helpers",
        "org.openkilda.performancetests.helpers",
        "org.openkilda.functionaltests.model.cleanup"
])class HelpersConfig {
}
