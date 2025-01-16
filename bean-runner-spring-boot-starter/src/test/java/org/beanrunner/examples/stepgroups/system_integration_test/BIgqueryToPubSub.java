package org.beanrunner.examples.stepgroups.system_integration_test;

import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepGroupAutowired;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepSize;
import org.beanrunner.examples.TestStep;
import org.beanrunner.examples.stepgroups.demo_service.CreateComputeResources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@StepSize(25)
@StepIcon("images/step-dataflow.svg")
public class BIgqueryToPubSub extends TestStep<Void> {

    @Autowired
    @OnSuccess
    private InsertDataToBigTable insertDataToBigTable;

    @StepGroupAutowired("test")
    @OnSuccess
    private CreateComputeResources createComputeResources;


}
