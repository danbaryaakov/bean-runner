package org.beanrunner.examples.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.beanrunner.ConcurrentRunsLimiter;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepSize;
import org.beanrunner.core.annotations.UIConfigurable;
import org.beanrunner.examples.rewind.Person;
import org.beanrunner.examples.rewind.RandomPersonGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@StepIcon("images/step-cog.svg")
@StepSize(30)
public class GeneratePerson extends Step<Person> {

    @Autowired
    @OnSuccess
    @Qualifier(HttpExampleConfig.LIMITER_QUALIFIER)
    private ConcurrentRunsLimiter wr1;

    @JsonProperty
    @UIConfigurable("Should fail")
    private boolean shouldFail;

    @JsonProperty
    @UIConfigurable("Delay")
    private boolean introduceDelay;

    @Override
    protected void run() {
        setData(RandomPersonGenerator.generateRandomPerson());
        if (introduceDelay) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (shouldFail) {
            throw new RuntimeException("BOOM!");
        }
    }

}
