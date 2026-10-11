---
name: vuln-scanner-agent
description: CVE vulnerability scanning
---
# /vuln-scanner-agent

Invoke the **VulnScannerAgent** cyber-security agent (sources vendored under `src/agent/cyber-security/VulnScannerAgent/`).

## Usage
```python
from vulnscanneragent import VulnScannerAgent
agent = VulnScannerAgent()
result = agent.describe()
```

## Capabilities
- Vulnerability scanning and exploitation
- Payload generation and delivery
- Post-exploitation and persistence
- Evasion techniques

Skill doc: `src/skill/cyber-agents/vuln-scanner-agent/SKILL.md`. Full sources: `src/agent/cyber-security/VulnScannerAgent/`.

