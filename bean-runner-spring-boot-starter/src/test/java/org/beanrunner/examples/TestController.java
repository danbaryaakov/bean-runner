package org.beanrunner.examples;

import org.beanrunner.examples.result.TestInvoker;
import org.beanrunner.examples.result.TestParameter;
import org.beanrunner.examples.result.TestResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {


    @Autowired
    private TestInvoker testInvoker;

    @GetMapping("/test")
    public TestResult getRequest() {
        return testInvoker.runSync(null);
    }

}
