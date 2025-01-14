package org.beanrunner.tasks.rewind;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepSize;
import org.beanrunner.tasks.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@StepSize(30)
@StepIcon("images/step-bigtable.svg")
public class R3 extends TestStep<Void> {

    @Autowired
    @OnSuccess
    private R23 r23;

    @Autowired
    @OnSuccess
    private R25 r25;

}
