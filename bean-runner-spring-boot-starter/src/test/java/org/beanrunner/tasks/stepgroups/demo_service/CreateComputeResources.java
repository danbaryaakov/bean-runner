package org.beanrunner.tasks.stepgroups.demo_service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.GroupTriggerStep;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.tasks.stepgroups.compute.ComputeEnvInput;
import org.beanrunner.tasks.stepgroups.pubsub.PubSubEnvCreated;

@Slf4j
@RequiredArgsConstructor
public class CreateComputeResources extends GroupTriggerStep<ComputeEnvInput> {

    @NonNull
    private String qualifier;

    @NonNull
    @OnSuccess
    private PubSubEnvCreated pubSubEnvCreatedExtractor;

    @NonNull
    @OnSuccess
    private PubSubEnvCreated pubSubEnvCreatedWriter;

    @Override
    public void run() {
        StepGroupData<ComputeEnvInput> data = new StepGroupData<>();
        data.getData().put(qualifier + "_service_1", ComputeEnvInput.builder()
                .instanceTemplateName("service-1-instance-template")
                .build());
        data.getData().put(qualifier + "_service_2", ComputeEnvInput.builder()
                .instanceTemplateName("service-2-instance-template")
                .build());
        setData(data);
    }

}
