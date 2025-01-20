package org.beanrunner.examples.stepgroups.system_integration_test;

import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.FailureBehavior;
import org.beanrunner.core.annotations.PauseableFlow;
import org.beanrunner.core.annotations.RunRetentionConfig;
import org.beanrunner.core.annotations.StepName;
import org.springframework.stereotype.Component;

@Component
@StepName("Integration Test")
@PauseableFlow(FailureBehavior.PAUSE)
@RunRetentionConfig(clearSuccessfulRuns = true, clearFailedRuns = true, failureTTLMillis = 0, successfulTTLMillis = 0)
public class DemoServiceIntegrationTest extends Step<Void> {

}
