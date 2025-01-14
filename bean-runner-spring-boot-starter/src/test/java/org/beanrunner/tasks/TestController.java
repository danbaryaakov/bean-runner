package org.beanrunner.tasks;

import org.beanrunner.tasks.result.TestInvoker;
import org.beanrunner.tasks.result.TestParameter;
import org.beanrunner.tasks.result.TestResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {


    @Autowired
    private TestInvoker testInvoker;

    @GetMapping("/test")
    public TestResult getRequest() {
        return testInvoker.runSync(new TestParameter("Parameter from flow"));
    }

}
