package org.beanrunner.examples.rewind;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepRewindTrigger;
import org.beanrunner.examples.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@StepRewindTrigger()
@StepIcon("images/step-pubsub.svg")
public class R4 extends TestStep<Void> {

    @Autowired
    @OnSuccess
    private R3 r3;

}
