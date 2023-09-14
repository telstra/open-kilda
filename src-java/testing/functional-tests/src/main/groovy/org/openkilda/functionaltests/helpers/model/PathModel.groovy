package org.openkilda.functionaltests.helpers.model

import groovy.transform.ToString
import groovy.transform.TupleConstructor

@TupleConstructor
@ToString(includeNames = true, includePackage = false)
class PathModel {
    Path forward
    Path reverse
}