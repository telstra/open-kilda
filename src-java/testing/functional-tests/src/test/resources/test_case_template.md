#### ${utils.getSpecClassName( data ).split('\\.').last() - ~/Spec$/}
<%
    def specTitle = utils.specAnnotation( data, spock.lang.Title )?.value()
    if ( specTitle ) {
        specTitle.split('\n').each { out << it << '\n' }
    }
    if ( data.info.narrative ) {
        if ( specTitle ) { out << '\n' }
        out << data.info.narrative << '\n'
    }
    features.eachFeature { name, result, blocks, iterations, params ->
        if(description.getAnnotation( org.openkilda.functionaltests.extension.healthcheck.HealthCheck ) ||
        description.getAnnotation( org.openkilda.functionaltests.extension.spring.PrepareSpringContextDummy )) {
            return
        }
 %>
* $name
  <%
        blocks.each { block ->
        if(block.kind == "Where:") {
            return
        }
  %>
  > ${block.kind} ${block.text}
  <%
        }
    }
  %>
