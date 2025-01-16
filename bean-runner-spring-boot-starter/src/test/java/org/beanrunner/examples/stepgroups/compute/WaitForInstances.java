package org.beanrunner.examples.stepgroups.compute;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.examples.TestStep;

@Slf4j
@RequiredArgsConstructor
@StepIcon("images/step-compute.svg")
public class WaitForInstances extends TestStep<Void> {

    @NonNull
    @OnSuccess
    private CreateInstanceGroup createInstanceGroup;

}
