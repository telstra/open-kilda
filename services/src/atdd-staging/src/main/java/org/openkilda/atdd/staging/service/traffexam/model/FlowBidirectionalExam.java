package org.openkilda.atdd.staging.service.traffexam.model;

import com.google.common.collect.ImmutableList;
import lombok.Value;

import java.util.List;

@Value
public class FlowBidirectionalExam {

    private Exam forward;
    private Exam reverse;

    public List<Exam> getExamPair() {
        return ImmutableList.of(forward, reverse);
    }
}
