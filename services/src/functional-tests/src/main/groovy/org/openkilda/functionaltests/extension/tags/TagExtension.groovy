package org.openkilda.functionaltests.extension.tags

import static org.openkilda.functionaltests.extension.ExtensionHelper.isFeatureSpecial

import org.openkilda.functionaltests.extension.spring.ContextAwareGlobalExtension

import groovy.util.logging.Slf4j
import org.junit.AssumptionViolatedException
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.SpecInfo

/**
 * A list of tags may be supplied to a build by setting 'tags' system property.
 * Tag expression is basically a boolean expression, that consists of typical operators AND, OR, NOT applied to
 * certain tags. List of allowed tags can be found in {@link Tag} enum. Also, round brackets can be used to build more
 * complex expressions.<br>
 * Example: {@code -Dtags="not ondemand or (positive and negative)"}<br>
 * In order to tag a certain test use annotation {@link Tags}, {@link IterationTags} or {@link IterationTag}.<br>
 *
 * Any spec will get its parent's tags, too. As well as any test method will get all the spec's tags.
 */
@Slf4j
class TagExtension extends AbstractGlobalExtension {

    static final String TAGS_PROPERTY_NAME = "tags"

    static Set<String> specialLiterals = ["NOT", "AND", "OR", "(", ")"]
    static Set<String> tagLiterals = Tag.values()*.toString()

    @Override
    void start() {
        //provide getNameTagged implementation for spec/feature/iteration names and inject Tags information
        [SpecInfo, FeatureInfo].each {
            it.metaClass.getNameTagged = { ->
                def tags = collectAllTagsAnnotations(delegate).collectMany { it.value().toList() }
                delegate.name + tagsCollectionToString(tags)
            }
        }
        IterationInfo.metaClass.getNameTagged = { ->
            def iteration = delegate as IterationInfo
            def featureMethod = iteration.feature.featureMethod
            def iterationTags = (featureMethod.getAnnotation(IterationTags)?.value()?.toList() ?: [] +
                    featureMethod.getAnnotation(IterationTag)).findAll()
            def applicableTags = iterationTags.findAll {
                iteration.name =~ it.iterationNameRegex()
            }.collectMany { it.tags().toList() }
            def tagsAnnotation = featureMethod.getAnnotation(Tags)
            if(tagsAnnotation) {
                applicableTags.addAll(tagsAnnotation.value().toList())
            }
            return iteration.name + tagsCollectionToString(applicableTags)
        }
    }

    @Override
    void visitSpec(SpecInfo spec) {
        String tagsExpression = System.getProperty(TAGS_PROPERTY_NAME)
        if (!tagsExpression) {
            return
        }
        spec.getAllFeatures().findAll { !isFeatureSpecial(it) }.each { feature ->
            if(feature.excluded) { //do not compete if feature is already excluded somehow
                return
            }
            def tags = collectAllTagsAnnotations(feature).collectMany { it.value().toList() } as Set
            def iterationTags = (feature.featureMethod.getAnnotation(IterationTags)?.value()?.toList() ?: [] +
                    feature.featureMethod.getAnnotation(IterationTag)).findAll()
            feature.excluded = !matches(tagsExpression, tags + iterationTags.collectMany {it.tags().toList() })
            if (iterationTags) {
                feature.addIterationInterceptor(new IMethodInterceptor() {
                    /*This stores how many times did we match a certain iteration tag.
                     Use this when calculating 'take' limitation for the iteration tag*/
                    Map<IterationTag, Integer> tagExecutions = iterationTags.collectEntries { [(it): 0] }

                    @Override
                    void intercept(IMethodInvocation invocation) throws Throwable {
                        def iteration = invocation.iteration
                        //look for iteration which matches our iteration name regexp
                        Map<IterationTag, Integer> applicableITags = tagExecutions.findAll { itag, exec ->
                            iteration.name =~ itag.iterationNameRegex()
                        }
                        //If no applicable iteration tags found, try checking whether top-level tags allow execution
                        if (applicableITags.isEmpty() && matches(tagsExpression, tags)) {
                            invocation.proceed()
                            return
                        }
                        //otherwise, look whether any of found iteration tags allow further execution
                        def allowingTag = applicableITags.find { itag, executions ->
                            matches(tagsExpression,
                                    (tags + applicableITags.keySet().collectMany { it.tags().toList() }) as Set) &&
                                    executions < itag.take()
                        }
                        if (allowingTag) {
                            allowingTag.value++
                            invocation.proceed()
                        } else {
                            throw new AssumptionViolatedException("The test '$iteration.feature.spec.name#" +
                                    "$iteration.name' does not match the provided tags expression: '$tagsExpression'")
                        }
                    }
                })
            }
        }
    }

    static Set<Tag> collectAllTags(IterationInfo iteration) {
        def feature = iteration.feature
        def iterationTags = (feature.featureMethod.getAnnotation(IterationTags)?.value()?.toList() ?: [] +
                feature.featureMethod.getAnnotation(IterationTag)).findAll()
        def applicableTags = iterationTags.findAll {
            iteration.name =~ it.iterationNameRegex()
        }.collectMany { it.tags().toList() }
        def tagsAnnotations = collectAllTagsAnnotations(feature)
        if(tagsAnnotations) {
            applicableTags.addAll(tagsAnnotations.collectMany { it.value().toList() })
        }
        return applicableTags as Set
    }

    static List<Tags> collectAllTagsAnnotations(FeatureInfo feature) {
        def tags = []
        def annotation = feature.featureMethod.getAnnotation(Tags)
        if (annotation) {
            tags << annotation
        }
        tags.addAll(collectAllTagsAnnotations(feature.featureMethod.getParent()))
        return tags
    }

    static List<Tags> collectAllTagsAnnotations(SpecInfo spec) {
        def tags = []
        def annotation = spec.getAnnotation(Tags)
        if (annotation) {
            tags << annotation
        }
        def superSpec = spec.getSuperSpec()
        if (superSpec) {
            tags.addAll(collectAllTagsAnnotations(superSpec))
        }
        return tags
    }

    /**
     * Check whether given tags expression evaluates into 'true' for given list of tags.
     */
    static boolean matches(String tagsExpression, Set<Tag> tags) {
        def literals = tagsExpression
                .replaceAll(/[()]/, / $0 /)
                .replaceAll(/\s+/, " ").trim()
                .split(" ")
        return Eval.me(literals.collect {
            def literal = it.toUpperCase()
            if (literal == "AND") {
                "&&"
            } else if (literal == "OR") {
                "||"
            } else if (literal == "NOT") {
                "!"
            } else if (literal in specialLiterals) {
                literal
            } else if (literal in tagLiterals) {
                if (tags.contains(Tag.valueOf(literal))) {
                    "true"
                } else {
                    "false"
                }
            } else {
                throw new UnknownTagLiteralException("Unknown literal: $literal")
            }
        }.join(" "))
    }

    private static String tagsCollectionToString(List<Tag> input) {
        if(input.empty) {
            return ""
        } else {
            return (input as Set)*.toString().inspect()
        }
    }
}
