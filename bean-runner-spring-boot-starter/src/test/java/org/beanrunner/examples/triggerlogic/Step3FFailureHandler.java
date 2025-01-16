package org.beanrunner.examples.triggerlogic;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnUpstreamFailure;
import org.beanrunner.examples.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Step3FFailureHandler extends TestStep<Void> {

    @Autowired
    @OnUpstreamFailure
    Step3F task3F;

}
