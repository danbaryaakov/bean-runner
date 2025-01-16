package org.beanrunner.examples.result;

import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.OnSuccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WR3 extends Step<TestResult> {

    @Autowired
    @OnSuccess
    private WR2 wr2;

    @Override
    public void run() {
        setData(new TestResult("Result from flow"));
    }
}
