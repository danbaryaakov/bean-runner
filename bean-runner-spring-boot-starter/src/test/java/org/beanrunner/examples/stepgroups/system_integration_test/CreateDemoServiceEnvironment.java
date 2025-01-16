package org.beanrunner.examples.stepgroups.system_integration_test;

import org.beanrunner.core.GroupTriggerStep;
import org.beanrunner.core.StepGroup;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.core.StepGroupGenerator;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.examples.stepgroups.demo_service.DemoServiceInput;
import org.beanrunner.examples.stepgroups.demo_service.DemoServiceStepGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CreateDemoServiceEnvironment extends GroupTriggerStep<DemoServiceInput> implements StepGroupGenerator {

    @Autowired
    @OnSuccess
    private DemoServiceIntegrationTest demoServiceIntegrationTest;

    @Override
    public void run() {
        StepGroupData<DemoServiceInput> data = new StepGroupData<>();
        data.put("test", DemoServiceInput.builder()
                .environmentId("test_environment")
                .build());
        setData(data);
    }

    @Override
    public List<StepGroup> generateStepGroups() {
        return DemoServiceStepGroup.generate("test", this);
    }
}
