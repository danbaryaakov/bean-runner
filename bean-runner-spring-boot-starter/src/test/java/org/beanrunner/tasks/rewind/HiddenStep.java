package org.beanrunner.tasks.rewind;

import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepHidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@StepHidden
public class HiddenStep extends Step<Void> {

    @Autowired
    @OnSuccess
    private R4 r4;
}
