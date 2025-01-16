package org.beanrunner.examples.stepgroups.compute;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.Step;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepSize;
import org.beanrunner.examples.TestStep;

@Slf4j
@RequiredArgsConstructor
@StepIcon("images/step-compute.svg")
@StepSize(25)
public class CreateInstanceTemplate extends TestStep<Void> {

    @NonNull
    private String qualifier;

    @NonNull
    @OnSuccess
    private Step<StepGroupData<ComputeEnvInput>> start;

    @Override
    public void run() {
        super.run();
        ComputeEnvInput input = start.getData().get(qualifier);
        log.info("Creating instance template: {}", input.getInstanceTemplateName());
    }

}
