---
name: CookieHarvesterAgent
description: >
  Cookie theft and session hijacking
triggers:
  - exploit
  - CookieHarvesterAgent
  - attack
  - vulnerability
category: red_teaming
auto_generated: true
enabled: true
metadata:
  created_at: "2026-08-28"
  agent: agents/specialized/cookieharvesteragent.py
---

# CookieHarvesterAgent

Cookie theft and session hijacking.

## Capabilities
- Vulnerability scanning and exploitation
- Payload generation and delivery
- Post-exploitation and persistence
- Evasion techniques

## Usage
```python
from cookieharvesteragent import CookieHarvesterAgent
agent = CookieHarvesterAgent()
result = agent.describe()
```
