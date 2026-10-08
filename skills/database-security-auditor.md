# Database Security Auditor

This skill guides an agent to perform deep security and performance audits on database interaction code using specialized review tools.

## Focus Areas
- **SQL Injection Prevention**: Identifying and fixing potential SQL injection vulnerabilities in raw SQL queries and ORM usage.
- **PostgreSQL Best Practices**: Auditing for PostgreSQL-specific anti-patterns, including improper JSONB usage, array manipulation, and custom type misuse.
- **Performance Tuning**: Analyzing queries for bottlenecks, suggesting indexing strategies, and identifying inefficient joins or full table scans.
- **Row Level Security (RLS)**: Reviewing policies to ensure data access is correctly enforced at the row level.
- **Code Review Integration**: Utilizing `sql-code-review` and `postgresql-code-review` to generate comprehensive audit reports.

## Workflow
1.  **Code Ingestion**: Receive the database interaction code snippet or file.
2.  **Security Scan**: Run the code through `sql-code-review` to check for injection risks and general SQL security anti-patterns.
3.  **PostgreSQL Deep Dive**: Run the code through `postgresql-code-review` to check for PostgreSQL-specific issues (JSONB, arrays, RLS).
4.  **Optimization Analysis**: Run the code through `sql-optimization` to analyze query performance and suggest indexing improvements.
5.  **Report Generation**: Consolidate findings from all tools into a single, actionable report for the agent.
6.  **Remediation Suggestion**: Propose specific code changes to fix identified issues.

## Guidance Principles
- **Security First**: Prioritize preventing data breaches and injection attacks.
- **Performance Driven**: Ensure all database operations are as performant as possible.
- **PostgreSQL Native**: Always consider the specific features and constraints of PostgreSQL when auditing.