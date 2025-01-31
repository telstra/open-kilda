package org.openkilda.functionaltests.helpers.model

import org.openkilda.northbound.dto.v1.flows.FlowValidationDto
import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto
import org.openkilda.northbound.dto.v2.haflows.HaFlowValidationResult
import org.openkilda.northbound.dto.v2.yflows.YFlowValidationResult

import groovy.transform.ToString

@ToString
class ComplexFlowValidationResponse {
    private static final String COOKIE_ID_IN_RULE_DISCREPANCY_STRING_REGEX =  /-?\d{19}/

    boolean asExpected
    List<SubFlowValidationDiscrepancies> subFlowsDiscrepancies

    ComplexFlowValidationResponse(YFlowValidationResult yFlowValidation) {
        this.asExpected = yFlowValidation.asExpected
        this.subFlowsDiscrepancies = collectDiscrepanciesPerSubFlow(yFlowValidation.subFlowValidationResults)
    }

    ComplexFlowValidationResponse(HaFlowValidationResult haFlowValidation) {
        this.asExpected = haFlowValidation.asExpected
        this.subFlowsDiscrepancies = collectDiscrepanciesPerSubFlow(haFlowValidation.subFlowValidationResults)

    }

    private static List<SubFlowValidationDiscrepancies> collectDiscrepanciesPerSubFlow(List<FlowValidationDto> subFlowValidationDetails) {
        List<SubFlowValidationDiscrepancies> discrepanciesPerSubFlow = subFlowValidationDetails.groupBy { it.flowId }.collect {
            assert (it.value.direction*.toUpperCase() as Set).size() == it.value.size(), "There is an error in the validation logic $it"
            HashMap<FlowDirection, List<PathDiscrepancyDto>> discrepancy = [:]
            it.value.each {
                verifyValidateLogic(it)
                it.asExpected ?: discrepancy.put(FlowDirection.getByDirection(it.direction.toLowerCase()), it.discrepancies)
            }
            discrepancy.isEmpty() ? null : new SubFlowValidationDiscrepancies(subFlowId: it.key, flowDiscrepancies: discrepancy)

        }.findAll()
        discrepanciesPerSubFlow
    }

    private static void verifyValidateLogic(FlowValidationDto validationDto) {
        assert (validationDto.asExpected && validationDto.discrepancies.isEmpty()) || (!validationDto.asExpected && !validationDto.discrepancies.isEmpty()),
                "There is an error in the validation logic $validationDto"
    }

    Set<Long> retrieveAllRulesCookieFromDiscrepancy() {
        def rules = subFlowsDiscrepancies.flowDiscrepancies*.values().flatten()
        rules.collect { new Long((it =~ COOKIE_ID_IN_RULE_DISCREPANCY_STRING_REGEX)[0]) }
    }
}
