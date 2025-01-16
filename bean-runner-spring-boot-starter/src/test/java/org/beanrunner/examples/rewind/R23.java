package org.beanrunner.examples.rewind;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.examples.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class R23 extends TestStep<Void> {

    @Autowired
    @OnSuccess
    private R2 r2;

}
