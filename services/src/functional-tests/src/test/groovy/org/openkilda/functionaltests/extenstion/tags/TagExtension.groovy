package org.openkilda.functionaltests.extenstion.tags

import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.model.MethodInfo
import org.spockframework.runtime.model.SpecInfo

/**
 * Use these system properties to leverage test suite by tags: includeTags, excludeTags.
 * Pass comma-separated list of properties as a value. If not passing a value to **includeTags**, then
 * all tests are included by default (even without tags). In order to get a list of available tags look at the
 * {@link Tag} enum. For example, to run slow+positive test suite, excluding
 * ondemand tests, execute the following command:
 * {@code mvn clean test -Pfunctional -DincludeTags=positive,slow -DexcludeTags=ondemand}
 * <br>
 * Any spec will get its parent's tags, too. As well as any test method will get all the spec's tags.
 */
@Slf4j
class TagExtension extends AbstractGlobalExtension {

    static final String INCLUDE_PROPERTY_NAME = "includeTags"
    static final String EXCLUDE_PROPERTY_NAME = "excludeTags"

    void visitSpec(SpecInfo spec) {
        List<Tag> includeTags = getBuildTags(INCLUDE_PROPERTY_NAME)
        List<Tag> excludeTags = getBuildTags(EXCLUDE_PROPERTY_NAME)
        if(includeTags != [null]) {
            spec.excluded = true
            spec.getAllFeatures().each { it.excluded = true }
        }
        spec.getAllFeatures().each { feature ->
            def tags = collectAllTags(feature.featureMethod)
            if(tags.containsAll(includeTags)) {
                spec.excluded = false
                feature.excluded = false
                log.debug("feature '$feature.name' included with tags $tags")
            }
            if(tags.containsAll(excludeTags)) {
                log.debug("feature '$feature.name' included with tags $tags")
                feature.excluded = true
                if(spec.getAllFeatures().every { it.excluded }) {
                    spec.excluded = true
                }
            }
        }
    }

    private Set<Tag> collectAllTags(MethodInfo method) {
        def tags = []
        def annotation = method.getAnnotation(Tags)
        if(annotation) {
            tags.addAll(annotation.value())
        }
        tags.addAll(collectAllSpecTags(method.getParent()))
        return tags as Set<Tag>
    }

    private Set<Tag> collectAllSpecTags(SpecInfo spec) {
        def tags = []
        def annotation = spec.getAnnotation(Tags)
        if(annotation) {
            tags.addAll(annotation.value())
        }
        def superSpec = spec.getSuperSpec()
        if(superSpec) {
            tags.addAll(collectAllSpecTags(superSpec))
        }
        return tags as Set<Tag>
    }

    private List<Tag> getBuildTags(propertyName) {
        return System.getProperty(propertyName, "").split(",").findAll{it != ""}.collect {
            Tag.valueOf(it.toUpperCase())
        } ?: [null]
    }
}
