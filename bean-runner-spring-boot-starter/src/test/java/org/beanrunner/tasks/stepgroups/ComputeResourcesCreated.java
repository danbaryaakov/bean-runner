package org.beanrunner.tasks.stepgroups;//package org.beanflow.tasks.stepgroups;
//
//import lombok.extern.slf4j.Slf4j;
//import org.beanflow.framework.StepGroup;
//import org.beanflow.framework.StepGroupData;
//import org.beanflow.framework.StepGroupGenerator;
//import org.beanflow.framework.annotations.OnSuccess;
//import org.beanflow.framework.annotations.StepGroupAutowired;
//import org.beanflow.framework.annotations.StepRewindTrigger;
//import org.beanflow.tasks.TestStep;
//import org.beanflow.tasks.stepgroups.compute.ComputeEnvInput;
//import org.beanflow.tasks.stepgroups.compute.ComputeStepGroups;
//import org.beanflow.tasks.stepgroups.compute.WaitForInstances;
//import org.beanflow.tasks.stepgroups.pubsub.EnableDeadLettering;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
//@Slf4j
//@Component
//@StepRewindTrigger
//public class ComputeResourcesCreated extends TestStep<Void> {
//
//    @StepGroupAutowired("extractor")
//    @OnSuccess
//    private WaitForInstances waitForInstancesExtractor;
//
//    @StepGroupAutowired("writer")
//    @OnSuccess
//    private WaitForInstances waitForInstancesWriter;
//
//
//}
