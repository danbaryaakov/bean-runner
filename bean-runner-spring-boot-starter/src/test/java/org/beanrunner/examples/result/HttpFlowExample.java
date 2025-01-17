package org.beanrunner.examples.result;

import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.RunRetentionConfig;
import org.beanrunner.core.annotations.StepDescription;
import org.beanrunner.core.annotations.StepName;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepName("Microservice Test")
@StepDescription("""
        This flow demonstrates programmatic invocation of a flow via HTTP request. 
        The API returns a random Person object.
        
        Invoke the flow by issuing a GET request to http://localhost:8080/person.
        
        You can also stress test the API performance using a tool like Postman's collection performance test feature.
        Experiment with enabling the Delay parameter in the Person Generator step and change the rate limit in the Rate Limiter to see how the flow behaves under load.
        
        The flow is configured with the @RunRetentionConfig annotation, configuring it to not retain any runs when invoked via HTTP.
        
        It also demonstrates an experimental batch processing step that's triggered once N data objects.
        are received from its input step. The reporter prints out the batch of generated persons to the console.
        """)
@RunRetentionConfig(showInUI = false, clearSuccessfulRuns = true, clearFailedRuns = true, successfulTTLMillis = 200, failureTTLMillis = 200)
public class HttpFlowExample extends Step<Void> {

    @Override
    public void run() {

    }

}
