package org.beanrunner.tasks.stepgroups;//package org.beanflow.tasks.stepgroups;
//
//import org.beanflow.framework.StepGroup;
//import org.beanflow.framework.StepGroupData;
//import org.beanflow.framework.StepGroupGenerator;
//import org.beanflow.framework.annotations.OnSuccess;
//import org.beanflow.tasks.TestStep;
//import org.beanflow.tasks.stepgroups.pubsub.PubSubStepGroupInput;
//import org.beanflow.tasks.stepgroups.pubsub.PubSubStepGroups;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.util.ArrayList;
//import java.util.List;
//
//@Component
//public class CreatePubSubResources extends TestStep<StepGroupData<PubSubStepGroupInput>> implements StepGroupGenerator {
//
//    @Autowired
//    @OnSuccess
//    private CreateDevEnv createDevEnv;
//
//    @Override
//    public void run() {
//        StepGroupData<PubSubStepGroupInput> data = new StepGroupData<>();
//        data.getData().put("extractor", PubSubStepGroupInput.builder()
//                .topicId(("extractor-topic"))
//                .build());
//        data.getData().put("writer", PubSubStepGroupInput.builder()
//                .topicId(("writer-topic"))
//                .build());
//        setData(data);
//    }
//
//    @Override
//    public List<StepGroup> generateStepGroups() {
//        return StepGroup.listOf(
//                PubSubStepGroups.generatePubSubGroup("extractor", this),
//                PubSubStepGroups.generatePubSubGroup("writer", this)
//        );
//    }
//
//}
