package org.beanrunner.tasks.stepgroups.demo_service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.tasks.TestStep;
import org.beanrunner.tasks.stepgroups.compute.WaitForInstances;

@Slf4j
@RequiredArgsConstructor
public class ServiceDeployed extends TestStep<Void> {

    @NonNull
    @OnSuccess
    private WaitForInstances waitForInstancesExtractor;

    @NonNull
    @OnSuccess
    private WaitForInstances waitForInstancesWriter;

}
