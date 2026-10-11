# DevOps Pipeline Orchestrator

This skill guides an agent in planning, building, and troubleshooting CI/CD pipelines for deployment, focusing on containerization and cloud deployment strategies.

## Focus Areas
- **CI/CD Planning**: Designing the overall pipeline structure, including stages (build, test, security scan, deploy).
- **Containerization**: Creating optimized multi-stage Dockerfiles for efficient image building.
- **Deployment Strategy**: Planning deployment to various environments (staging, production) using infrastructure-as-code principles.
- **Troubleshooting**: Diagnosing failures in pipeline execution, Docker build errors, and cloud deployment issues.
- **Best Practices**: Implementing security scanning (SAST/SCA) and ensuring production readiness checks are integrated.

## Workflow
1.  **Goal Definition**: Define the target deployment environment and required artifacts.
2.  **Pipeline Blueprint**: Generate a high-level plan using `gem-devops` to define the sequence of steps.
3.  **Build Stage**: Generate optimized multi-stage Dockerfiles and build the container image.
4.  **Testing Stage**: Define and execute unit/integration tests within the pipeline.
5.  **Security Gate**: Integrate static analysis (`sonar-analyze`, `sonar-dependency-risks`) into the pipeline.
6.  **Deployment Execution**: Plan and execute the deployment using deployment tools (e.g., `deploy-pipeline`).
7.  **Troubleshooting**: If a pipeline fails, use `azure-resource-health-diagnose` or `debug-deployment` to diagnose the root cause.

## Guidance Principles
- **Automation**: Maximize automation for repeatable, reliable deployments.
- **Security by Design**: Integrate security checks early and often in the pipeline.
- **Idempotency**: Ensure pipeline steps are idempotent to allow safe re-runs.