
![Screenshot](/site/screenshot.png)

BeanRunner is a workflow automation orchestrator in a spring boot starter. Once added to a 
spring boot project, it allows you to define flows by creating interconnected spring beans annotated with
run dependency annotations such as @OnSuccess, @OnComplete, @OnFailure etc. The flows form a DAG (Directed Acyclic Graph) that can then
be triggered by the UI, by a defining a CRON schedule, or invoked programmatically.

BeanRunner's flow executor handles parallelism, retries, error handling, as well
as resource cleanup on failures and conditionally on success with a 'rewind' feature.

BeanRunner provides a comprehensive UI for monitoring and managing the flows, viewing real time status and logs for each step
in the flow, and also provides flow run execution history. It also allows you to configure
step parameters at runtime.

The project is still in its early stages, and further development is needed to make it production ready, such as 
adding security features, handling graceful shutdowns, and some additional required features to make it complete.

The code is free to use and modify, and contributions are welcome. It is licensed under the LGPL v3.0 license.

## Getting Started

### Create a Spring Boot Vaadin Project
BeanRunner uses the Vaadin framework for it's UI layer, so you need to create a Vaadin project. Don't worry, you don't have to know anything about UI development when using BeanRunner, and creating such a project is easy:
#### Using Intellij IDEA
* File -> New -> Project...
* Select the "Spring" generator on the left side panel
* Enter your project details
* Click the "Next" button
* In the left side panel (Dependencies), expand "Web" and check "Vaadin"
* Click "Create"
#### Using the Vaadin 24 starter project template
* Download the "Vaadin 24 - Spring Boot" starter project from https://vaadin.com/hello-world-starters

### Add the BeanRunner Dependency

Add the GitHub packages repository in your project's build.gradle file's `repositories` section:
```groovy
maven {
    name = "GitHubPackages"
    url = uri("https://maven.pkg.github.com/danbaryaakov/maven-packages/")
    credentials {
        username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
        password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
    }
}
```

(Make sure you have a GitHub personal access token with read:packages scope, and set the GITHUB_USERNAME and GITHUB_TOKEN environment variables)

Add the following dependency in the `dependencies` section:
```groovy
implementation 'org.bean-runner:bean-runner-spring-boot-starter:0.0.3'
```

### Configure Your Main Application Class

Add the `@EnableVaadin({"org.beanrunner"})` annotation to your main application class 

Add the `@EnableScheduling` annotation if you want to use CRON scheduling for your flows. 

Add the `"org.beanrunner"` package to the `scanBasePackages` attribute of the `@SpringBootApplication` annotation as in the example below:

```java
@SpringBootApplication(scanBasePackages = {"org.beanrunner", "com.my.package"})
@EnableScheduling
@EnableVaadin({"org.beanrunner"})
public class MySpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(MySpringApplication.class, args);
    }

}
```

More documentation coming soon...