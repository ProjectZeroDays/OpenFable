---
name: messaging-rce-agent
description: Messaging protocol RCE exploitation
---
# /messaging-rce-agent

Invoke the **MessagingRCEAgent** cyber-security agent (sources vendored under `src/agent/cyber-security/MessagingRCEAgent/`).

## Usage
```python
from messagingrceagent import MessagingRCEAgent
agent = MessagingRCEAgent()
result = agent.describe()
```

## Capabilities
- Vulnerability scanning and exploitation
- Payload generation and delivery
- Post-exploitation and persistence
- Evasion techniques

Skill doc: `src/skill/cyber-agents/messaging-rce-agent/SKILL.md`. Full sources: `src/agent/cyber-security/MessagingRCEAgent/`.

