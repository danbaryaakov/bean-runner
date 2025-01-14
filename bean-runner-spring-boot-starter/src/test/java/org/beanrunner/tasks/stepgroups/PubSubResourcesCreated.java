package org.beanrunner.tasks.stepgroups;//package org.beanflow.tasks.stepgroups;
//
//import lombok.NonNull;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.beanflow.framework.StepGroup;
//import org.beanflow.framework.StepGroupData;
//import org.beanflow.framework.StepGroupGenerator;
//import org.beanflow.framework.annotations.OnComplete;
//import org.beanflow.framework.annotations.OnSuccess;
//import org.beanflow.framework.annotations.StepGroupAutowired;
//import org.beanflow.framework.annotations.StepRewindTrigger;
//import org.beanflow.tasks.TestStep;
//import org.beanflow.tasks.stepgroups.compute.ComputeEnvInput;
//import org.beanflow.tasks.stepgroups.compute.ComputeStepGroups;
//import org.beanflow.tasks.stepgroups.pubsub.CreatePubSubSubscription;
//import org.beanflow.tasks.stepgroups.pubsub.EnableDeadLettering;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
//@Slf4j
//@Component
//public class PubSubResourcesCreated extends TestStep<StepGroupData<ComputeEnvInput>> implements StepGroupGenerator {
//
//    @StepGroupAutowired("extractor")
//    @OnSuccess
//    private EnableDeadLettering enableDeadLetteringExtractor;
//
//    @StepGroupAutowired("writer")
//    @OnSuccess
//    private EnableDeadLettering enableDeadLetteringWriter;
//
//    @Override
//    public void run() {
//        StepGroupData<ComputeEnvInput> data = new StepGroupData<>();
//        data.getData().put("extractor", ComputeEnvInput.builder()
//                .instanceTemplateName("extractor-instance-template")
//                .build());
//        data.getData().put("writer", ComputeEnvInput.builder()
//                .instanceTemplateName("writer-instance-template")
//                .build());
//        setData(data);
//    }
//
//    @Override
//    public List<StepGroup> generateStepGroups() {
//        return StepGroup.listOf(
//                ComputeStepGroups.generateComputeGroup("extractor", this),
//                ComputeStepGroups.generateComputeGroup("writer", this)
//        );
//    }
//
//}
