package org.beanrunner.examples.result;

import org.beanrunner.core.FlowInvoker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestInvoker extends FlowInvoker<TestParameter, TestResult> {

    public TestInvoker(@Autowired WR1 firstStep, @Autowired WR3 lastStep) {
        super(firstStep, lastStep);
    }

}
