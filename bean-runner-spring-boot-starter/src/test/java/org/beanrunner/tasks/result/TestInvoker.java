package org.beanrunner.tasks.result;

import org.beanrunner.core.StepInvoker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestInvoker extends StepInvoker<TestParameter, TestResult> {

    public TestInvoker(@Autowired WR1 firstStep, @Autowired WR3 lastStep) {
        super(firstStep, lastStep);
    }

}
