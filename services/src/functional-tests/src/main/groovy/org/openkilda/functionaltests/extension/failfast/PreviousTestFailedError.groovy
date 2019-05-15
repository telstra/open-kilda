package org.openkilda.functionaltests.extension.failfast

import groovy.transform.InheritConstructors

@InheritConstructors
class PreviousTestFailedError extends RuntimeException {
}
