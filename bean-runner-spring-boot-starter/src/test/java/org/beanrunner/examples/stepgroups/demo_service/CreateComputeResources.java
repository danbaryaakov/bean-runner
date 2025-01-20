package org.beanrunner.examples.stepgroups.demo_service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.GroupTriggerStep;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.UIConfigurable;
import org.beanrunner.examples.stepgroups.compute.ComputeEnvInput;
import org.beanrunner.examples.stepgroups.pubsub.PubSubEnvCreated;

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

    @UIConfigurable("Probe should wait")
    @JsonProperty
    private boolean probeShouldWait;

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

    @Override
    public boolean probe() {
        log.info("In probe... checking configuration value");
        return !probeShouldWait;
    }

}
