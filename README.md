
![Screenshot](/site/screenshot.png)

BeanRunner is a workflow automation orchestrator in a spring boot starter. 

Once added to a spring boot project, flows can be defined by creating interconnected spring beans annotated with
run dependency annotations such as `@OnSuccess`, `@OnComplete`, `@OnFailure` etc. The flows form a DAG (Directed Acyclic Graph) that can then
be triggered by the UI, by defining a CRON schedule, or invoked programmatically from pretty much any source.

BeanRunner's flow executor handles parallelism, retries, error handling, pause and resume, as well
as resource cleanup on failures and conditionally on success with a 'rewind' feature.

BeanRunner provides a comprehensive UI for monitoring and managing the flows, viewing real time status and logs for each step
in the flow, and also provides flow run execution history. It also allows you to configure
step parameters at runtime.

The project is still in its early stages, and further development is needed to make it production ready, such as 
adding security, authentication and some additional required features to make it complete.

The code is free to use and modify, and contributions are welcome. It is licensed under the LGPL v3.0 license.

To start learning how to build flows read the **[Getting Started](site/GETTING_STARTED.md)** guide.

The repository also contains a runnable example application with many flows that can help you get started building your own 
or testing any changes you make to the code.

The example application can be found at `bean-runner-spring-boot-starter/src/test/java/org/beanrunner/examples/BeanRunnerTestApplication`

If you're using Intellij IDEA, make sure you install the `Vaadin` plugin to support live reload of the UI when making changes in debug mode.
