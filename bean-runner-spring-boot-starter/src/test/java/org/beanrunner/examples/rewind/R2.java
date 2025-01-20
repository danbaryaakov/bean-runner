package org.beanrunner.examples.rewind;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.examples.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class R2 extends TestStep<R2.Data> {

    @Autowired
    @OnSuccess
    private R1 r1;

    @Override
    public void run() {
        setRunProperty("Some Property", "Some Value");
        setData(new Data("Hello, this is some data from step 2"));
        super.run();
    }

    record Data(String message) {}
}
