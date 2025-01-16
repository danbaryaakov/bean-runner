package org.beanrunner.examples.triggerlogic;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepRewindTrigger;
import org.beanrunner.examples.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@StepRewindTrigger
public class Step3F extends TestStep<Void> {

    @Autowired
    @OnSuccess
    Step3E task3E;

}
