package org.beanrunner.tasks.triggerlogic;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepTag;
import org.beanrunner.core.annotations.StepTagStyle;
import org.beanrunner.tasks.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@StepTag(value = "branch 1", style = StepTagStyle.PURPLE)
public class Step3C extends TestStep<Void> {

    @Autowired
    @OnSuccess("branch_1")
    Step3B task3B;

}
