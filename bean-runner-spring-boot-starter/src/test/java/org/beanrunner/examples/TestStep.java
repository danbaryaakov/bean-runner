package org.beanrunner.examples;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.UIConfigurable;

import static oshi.util.Util.sleep;

@Slf4j
public class TestStep<D> extends Step<D> {

    @JsonProperty
    @UIConfigurable("Simulate Failure")
    protected boolean isFail;

    @JsonProperty
    @UIConfigurable("Simulate Rewind Failure")
    protected boolean isRewindFail;

    @Override
    public void run() {
        log.info("Starting {}", getClass().getSimpleName());
        sleep(1000);
        if (isFail) {
            throw new RuntimeException("Simulating failure");
        }
        log.info("{} Done", getClass().getSimpleName());
    }

    @Override
    public void rewind() {
        log.info("Rewinding {}", getClass().getSimpleName());
        sleep(1000);
        if (isRewindFail) {
            throw new RuntimeException("Simulating rewind failure");
        }
        log.info("{} Rewind Done", getClass().getSimpleName());
    }
}
