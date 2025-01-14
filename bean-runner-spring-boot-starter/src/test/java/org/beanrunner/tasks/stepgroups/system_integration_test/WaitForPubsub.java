package org.beanrunner.tasks.stepgroups.system_integration_test;

import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepGroupAutowired;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepSize;
import org.beanrunner.tasks.TestStep;
import org.beanrunner.tasks.stepgroups.demo_service.ServiceDeployed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@StepIcon("images/step-pubsub.svg")
@StepSize(35)
public class WaitForPubsub extends TestStep<Void> {

    @Autowired
    @OnSuccess
    private BIgqueryToPubSub bIgqueryToPubSub;

    @OnSuccess
    @StepGroupAutowired("test")
    private ServiceDeployed serviceDeployed;

}
