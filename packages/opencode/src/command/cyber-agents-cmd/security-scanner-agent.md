---
name: security-scanner-agent
description: Security vulnerability scanning
---
# /security-scanner-agent

Invoke the **SecurityScannerAgent** cyber-security agent (sources vendored under `src/agent/cyber-security/SecurityScannerAgent/`).

## Usage
```python
from securityscanneragent import SecurityScannerAgent
agent = SecurityScannerAgent()
result = agent.describe()
```

## Capabilities
- Vulnerability scanning and exploitation
- Payload generation and delivery
- Post-exploitation and persistence
- Evasion techniques

Skill doc: `src/skill/cyber-agents/security-scanner-agent/SKILL.md`. Full sources: `src/agent/cyber-security/SecurityScannerAgent/`.

