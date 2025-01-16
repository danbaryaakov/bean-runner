package org.beanrunner.examples.result;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.StepDescription;
import org.beanrunner.core.annotations.StepName;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepName("Programmatic Invocation")
@StepDescription("""
        This flow demonstrates programmatic invocation of a flow.
        
        The flow in this example can be invoked by issuing a GET request to
        http://localhost:8080/test
        """)
public class WR1 extends Step<TestParameter> {

    @Override
    public void run() {
        log.info("Parameter is {}", getData().getName());
    }
}
