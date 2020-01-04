package org.openkilda.functionaltests.helpers

import groovy.transform.Canonical
import groovy.util.logging.Slf4j

import java.util.concurrent.Callable

/**
 * Allows to randomly execute one of the specified events. Accepts list of callable events with their chances to
 * appear during the 'roll'
 */
@Slf4j
class Dice {
    List<Face> faces
    Random r = new Random()

    Dice(List<Face> faces) {
        assert faces.sum { it.chance } == 100, "Sum of all event chances must be equal to 100"
        def filteredFaces = faces.findAll { it.chance > 0 }
        filteredFaces[1..-1].eachWithIndex { e, i ->
            e.chance += filteredFaces[i].chance
        }
        this.faces = filteredFaces.sort { it.chance }
    }

    def roll() {
        def roll = r.nextInt(100)
        return faces.find { it.chance > roll }.event()
    }

    @Canonical
    static class Face {
        String name
        Integer chance
        Callable event
    }
}
