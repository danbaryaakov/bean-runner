package org.beanrunner.tasks.stepgroups.deployment_env;

import org.beanrunner.core.Step;
import org.beanrunner.core.StepGroup;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.core.StepGroupGenerator;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.tasks.stepgroups.demo_service.DemoServiceInput;
import org.beanrunner.tasks.stepgroups.demo_service.DemoServiceStepGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeployDemoService extends Step<StepGroupData<DemoServiceInput>> implements StepGroupGenerator {

    @Autowired
    @OnSuccess
    private DeployEnvExample deployEnvExample;

    @Override
    public void run() {
        StepGroupData<DemoServiceInput> data = new StepGroupData<>();
        data.getData().put("demo1", DemoServiceInput.builder()
                .environmentId(deployEnvExample.getData().getEnvironmentID())
                .build());
        setData(data);
    }

    @Override
    public List<StepGroup> generateStepGroups() {
        return DemoServiceStepGroup.generate("demo1", this);
    }

}
