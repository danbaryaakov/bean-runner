package org.beanrunner.tasks.rewind;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepGroup;
import org.beanrunner.core.annotations.StepRetry;
import org.beanrunner.core.annotations.UIConfigurable;
import org.beanrunner.tasks.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@StepRetry(maxRetries = 6)
@StepGroup(1)
public class R24 extends TestStep<Void> {

    @Autowired
    @OnSuccess
    private R22 r22;

    @JsonProperty
    @UIConfigurable("Some Setting")
    private String someSetting;

    @Override
    public void run() {
        super.run();
        log.info("Running R24");
        log.info("Some setting value is: {}", someSetting);
    }
}
