package org.beanrunner.examples.stepgroups.deployment_env;

import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepGroupAutowired;
import org.beanrunner.core.annotations.StepRewindTrigger;
import org.beanrunner.core.annotations.StepRewindType;
import org.beanrunner.examples.stepgroups.demo_service.ServiceDeployed;
import org.springframework.stereotype.Component;

@Component
@StepRewindTrigger(StepRewindType.MANUAL)
public class ServiceEnvironmentDeployed extends Step<Void> {

    @StepGroupAutowired("demo1")
    @OnSuccess
    private ServiceDeployed serviceDeployed;

}
