/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.testing.service.traffexam;

import org.openkilda.testing.service.traffexam.model.Exam;
import org.openkilda.testing.service.traffexam.model.ExamReport;
import org.openkilda.testing.service.traffexam.model.ExamResources;
import org.openkilda.testing.service.traffexam.model.Host;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("virtual")
public class TraffExamVirtualServiceImpl implements TraffExamService {
    private RuntimeException unsupportedException = new UnsupportedOperationException(
            "Traff exam is not available yet for virtual topology");

    @Override
    public List<Host> listHosts() {
        throw unsupportedException;
    }

    @Override
    public Host hostByName(String name) {
        throw unsupportedException;
    }

    @Override
    public ExamResources startExam(Exam exam) {
        throw unsupportedException;
    }

    @Override
    public ExamReport waitExam(Exam exam) {
        throw unsupportedException;
    }

    @Override
    public ExamReport waitExam(Exam exam, boolean cleanup) {
        throw unsupportedException;
    }

    @Override
    public ExamReport fetchReport(Exam exam) {
        throw unsupportedException;
    }

    @Override
    public void stopExam(Exam exam) {
        throw unsupportedException;
    }

    @Override
    public void stopAll() {
        throw unsupportedException;
    }
}
