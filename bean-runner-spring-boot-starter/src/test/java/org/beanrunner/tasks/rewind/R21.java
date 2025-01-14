package org.beanrunner.tasks.rewind;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepGroup;
import org.beanrunner.tasks.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@StepGroup(value = 1, name = "Chain 2", icon = "images/step-dataflow.svg")
public class R21 extends TestStep<Void> {

    @Autowired
    @OnSuccess
    private R2 r2;

}
