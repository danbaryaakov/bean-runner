package org.beanrunner.examples.stepgroups.deployment_env;

import org.beanrunner.core.annotations.FailureBehavior;
import org.beanrunner.core.annotations.PauseableFlow;
import org.beanrunner.examples.TestStep;
import org.springframework.stereotype.Component;

@Component
@PauseableFlow(FailureBehavior.PAUSE)
public class DeployEnvExample extends TestStep<DeploymentParameters> {

}
