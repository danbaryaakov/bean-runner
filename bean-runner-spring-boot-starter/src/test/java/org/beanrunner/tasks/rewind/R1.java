package org.beanrunner.tasks.rewind;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.StepDescription;
import org.beanrunner.core.annotations.StepName;
import org.beanrunner.core.annotations.StepSchedule;
import org.beanrunner.core.annotations.UIConfigurable;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@StepName("Rewind Example")
@StepDescription("""
        This is an example of rewind functionality.
        
        The task graph will rewind on failures or successful completion, triggering
        resource cleanup if necessary.
        """)
@StepSchedule("0 * * * * *")
public class R1 extends Step<Void> {

    @JsonProperty
    @UIConfigurable(value = "Some Configuration")
    private String someConfiguration;

    @Override
    public void run() {

    }
}
