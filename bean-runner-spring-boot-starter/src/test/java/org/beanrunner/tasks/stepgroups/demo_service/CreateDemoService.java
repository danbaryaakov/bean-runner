package org.beanrunner.tasks.stepgroups.demo_service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.beanrunner.core.Step;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.core.annotations.OnSuccess;

@RequiredArgsConstructor
public class CreateDemoService extends Step<Void> {

    @NonNull
    private String qualifier;

    @NonNull
    @OnSuccess
    private Step<StepGroupData<DemoServiceInput>> inputStep;


    @Override
    public void run() {
        DemoServiceInput input = inputStep.getData().get(qualifier);
        System.out.println("Creating demo service: " + input.getEnvironmentId());
    }

}
