package org.openkilda.functionaltests.helpers.model

import groovy.transform.Canonical
import groovy.transform.ToString
import groovy.transform.TupleConstructor

@Canonical
@TupleConstructor
@ToString(includeNames = true, includePackage = false)
class PathModel {
    Path forward
    Path reverse

    boolean isPathAbsent() {
        forward?.nodes?.nodes?.isEmpty() && reverse?.nodes?.nodes?.isEmpty()
    }
}