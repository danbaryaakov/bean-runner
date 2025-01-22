package org.beanrunner.examples.stepgroups.deployment_env;

import org.beanrunner.core.annotations.FailureBehavior;
import org.beanrunner.core.annotations.FlowPauseBehavior;
import org.beanrunner.examples.TestStep;
import org.springframework.stereotype.Component;

@Component
@FlowPauseBehavior(failureBehavior = FailureBehavior.PAUSE)
public class DeployEnvExample extends TestStep<DeploymentParameters> {

}
