package org.beanrunner.examples.stepgroups.system_integration_test;

import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.StepName;
import org.springframework.stereotype.Component;

@Component
@StepName("Integration Test")
public class DemoServiceIntegrationTest extends Step<Void> {

}
