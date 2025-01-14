package org.beanrunner.tasks.triggerlogic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.UIConfigurable;
import org.beanrunner.tasks.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Step3B extends TestStep<Void> {

    @Autowired
    @OnSuccess
    Step3A task3A;

    @JsonProperty
    @UIConfigurable(value = "branch 2")
    private boolean isBranch2;

    @Override
    public void run() {
        super.run();
        setResult(isBranch2 ? "branch_2": "branch_1");
    }
}
