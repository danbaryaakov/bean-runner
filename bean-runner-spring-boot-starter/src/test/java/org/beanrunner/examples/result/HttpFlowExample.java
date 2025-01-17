package org.beanrunner.examples.result;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.RunRetentionConfig;
import org.beanrunner.core.annotations.StepDescription;
import org.beanrunner.core.annotations.StepName;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepName("HTTP Service")
@StepDescription("""
        This flow demonstrates programmatic invocation of a flow. via HTTP.
        
        Invoke the flow by issuing a GET request to http://localhost:8080/person.
        
        The flow is configured with the @RunRetentionConfig annotation, configuring it to retain only failed runs.
        Successful runs will be cleared after a second and not stored persistently.
        
        It also demonstrates an experimental batch processing step that's triggered once N data objects 
        are received from its input step.
        """)
@RunRetentionConfig(showInUI = false, clearSuccessfulRuns = true, clearFailedRuns = true, successfulTTLMillis = 200, failureTTLMillis = 200)
public class HttpFlowExample extends Step<Void> {

    @Override
    public void run() {

    }

}
