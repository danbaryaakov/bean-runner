package org.beanrunner.tasks.rewind;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.tasks.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class R2 extends TestStep<Void> {

    @Autowired
    @OnSuccess
    private R1 r1;

    @Override
    public void run() {
        setRunProperty("Some Property", "Some Value");
        super.run();
    }
}
