package org.beanrunner.tasks.triggerlogic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.tasks.TestStep;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepLogicOperator;
import org.beanrunner.core.annotations.StepTriggerLogic;
import org.beanrunner.core.annotations.UIConfigurable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@StepTriggerLogic(StepLogicOperator.OR)
public class Step3E extends TestStep<Void> {

    @JsonProperty
    @UIConfigurable(value = "Probe Should Wait")
    private boolean probeShouldWait;

    @JsonProperty
    @UIConfigurable(value = "Fail Probe")
    private boolean failProbe;

    @Autowired
    @OnSuccess
    Step3C task3C;

    @Autowired
    @OnSuccess
    Step3D task3D;

    @Override
    public boolean probe() {
        log.info("In probe... checking configuration value");
        if (failProbe) {
            throw new RuntimeException("Throwing from probe");
        }
        return !probeShouldWait;
    }
}
