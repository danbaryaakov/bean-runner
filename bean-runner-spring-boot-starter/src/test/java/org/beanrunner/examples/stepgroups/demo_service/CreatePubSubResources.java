package org.beanrunner.examples.stepgroups.demo_service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.beanrunner.core.Step;
import org.beanrunner.core.StepGroupData;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.examples.stepgroups.pubsub.PubSubStepGroupInput;

@RequiredArgsConstructor
public class CreatePubSubResources extends Step<StepGroupData<PubSubStepGroupInput>> {

    @NonNull
    private String qualifier;

    @NonNull
    @OnSuccess
    private CreateDemoService createDevEnv;

    @Override
    public void run() {
        StepGroupData<PubSubStepGroupInput> data = new StepGroupData<>();
        data.getData().put(qualifier + "_service_1", PubSubStepGroupInput.builder()
                .topicId(("extractor-topic"))
                .build());
        data.getData().put(qualifier + "_service_2", PubSubStepGroupInput.builder()
                .topicId(("writer-topic"))
                .build());
        setData(data);
    }

}
