---
name: BruteForceAgent
description: >
  GPU-accelerated brute force and password cracking
triggers:
  - exploit
  - BruteForceAgent
  - attack
  - vulnerability
category: red_teaming
auto_generated: true
enabled: true
metadata:
  created_at: "2026-08-28"
  agent: agents/specialized/bruteforceagent.py
---

# BruteForceAgent

GPU-accelerated brute force and password cracking.

## Capabilities
- Vulnerability scanning and exploitation
- Payload generation and delivery
- Post-exploitation and persistence
- Evasion techniques

## Usage
```python
from bruteforceagent import BruteForceAgent
agent = BruteForceAgent()
result = agent.describe()
```
