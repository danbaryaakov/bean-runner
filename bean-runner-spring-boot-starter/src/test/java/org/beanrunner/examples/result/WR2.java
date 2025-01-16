package org.beanrunner.examples.result;

import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.OnSuccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WR2 extends Step<TestParameter> {

    @Autowired
    @OnSuccess
    private WR1 wr1;

}
