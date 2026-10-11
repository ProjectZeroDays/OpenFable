# Backend Logic Specialist

This skill guides an agent in writing high-quality, production-ready backend code for services, focusing on Python, FastAPI, and SQLAlchemy.

## Focus Areas
- **API Design**: Designing RESTful endpoints, proper request/response schemas (using Pydantic), and adherence to API best practices.
- **SQLAlchemy Optimization**: Writing efficient SQLAlchemy queries, understanding lazy vs. eager loading, and optimizing ORM usage for performance.
- **Robust Error Handling**: Implementing comprehensive exception handling, custom error responses, and logging strategies for production stability.
- **Dependency Management**: Ensuring proper dependency injection and managing external service interactions securely.

## Workflow
1.  **Requirement Analysis**: Analyze the user's goal to define the required API endpoints and data models.
2.  **Schema Definition**: Generate Pydantic models for request/response validation.
3.  **Service Implementation**: Write the core logic using FastAPI and SQLAlchemy, focusing on clean separation of concerns (routes, services, database access).
4.  **Optimization Review**: Review generated SQLAlchemy queries for N+1 issues or inefficient joins.
5.  **Error Handling Implementation**: Integrate custom exception handlers to return appropriate HTTP status codes and error messages.
6.  **Code Review**: Perform a final review against FastAPI/SQLAlchemy best practices.

## Guidance Principles
- **Clarity**: Code must be readable and follow PEP 8 standards.
- **Performance**: Prioritize database efficiency over simple syntax.
- **Security**: Always sanitize inputs and handle database interactions with parameterized queries to prevent SQL injection.