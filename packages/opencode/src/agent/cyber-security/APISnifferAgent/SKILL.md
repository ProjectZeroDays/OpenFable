---
name: APISnifferAgent
description: >
  API traffic interception and analysis
triggers:
  - exploit
  - APISnifferAgent
  - attack
  - vulnerability
category: red_teaming
auto_generated: true
enabled: true
metadata:
  created_at: "2026-08-28"
  agent: agents/specialized/apisnifferagent.py
---

# APISnifferAgent

API traffic interception and analysis.

## Capabilities
- Vulnerability scanning and exploitation
- Payload generation and delivery
- Post-exploitation and persistence
- Evasion techniques

## Usage
```python
from apisnifferagent import APISnifferAgent
agent = APISnifferAgent()
result = agent.describe()
```
