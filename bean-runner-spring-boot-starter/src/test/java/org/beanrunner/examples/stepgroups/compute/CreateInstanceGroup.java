package org.beanrunner.examples.stepgroups.compute;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepSize;
import org.beanrunner.examples.TestStep;

@Slf4j
@RequiredArgsConstructor
@StepSize(30)
@StepIcon("images/step-compute.svg")
public class CreateInstanceGroup extends TestStep<Void> {

    @NonNull
    @OnSuccess
    private CreateInstanceTemplate createInstanceTemplate;

}
