package org.beanrunner.tasks.stepgroups.compute;

import org.beanrunner.core.Step;
import org.beanrunner.core.StepGroup;
import org.beanrunner.core.StepGroupData;

import java.util.List;

public class ComputeStepGroups {

    public static StepGroup generateComputeGroup(String qualifier, Step<StepGroupData<ComputeEnvInput>> previous) {
        CreateInstanceTemplate createInstanceTemplate = new CreateInstanceTemplate(qualifier, previous);
        CreateInstanceGroup createInstanceGroup = new CreateInstanceGroup(createInstanceTemplate);
        WaitForInstances waitForInstances = new WaitForInstances(createInstanceGroup);

        return StepGroup.builder()
                .qualifier(qualifier)
                .name("Compute " + qualifier)
                .iconPath("images/step-compute.svg")
                .steps(List.of(
                        createInstanceTemplate,
                        createInstanceGroup,
                        waitForInstances
                )).build();
    }
}
