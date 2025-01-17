package org.beanrunner.examples.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.UIConfigurable;
import org.beanrunner.examples.rewind.Person;
import org.beanrunner.examples.rewind.RandomPersonGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WR2 extends Step<Person> {

    @Autowired
    @OnSuccess
    private WR1 wr1;

    @JsonProperty
    @UIConfigurable("Should fail")
    private boolean shouldFail;

    @Override
    protected void run() {
        setData(RandomPersonGenerator.generateRandomPerson());
        if (shouldFail) {
            throw new RuntimeException("BOOM!");
        }
    }

}
