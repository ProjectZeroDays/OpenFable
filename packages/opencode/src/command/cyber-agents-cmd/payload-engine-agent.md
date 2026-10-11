---
name: payload-engine-agent
description: Payload generation and obfuscation
---
# /payload-engine-agent

Invoke the **PayloadEngineAgent** cyber-security agent (sources vendored under `src/agent/cyber-security/PayloadEngineAgent/`).

## Usage
```python
from payloadengineagent import PayloadEngineAgent
agent = PayloadEngineAgent()
result = agent.describe()
```

## Capabilities
- Vulnerability scanning and exploitation
- Payload generation and delivery
- Post-exploitation and persistence
- Evasion techniques

Skill doc: `src/skill/cyber-agents/payload-engine-agent/SKILL.md`. Full sources: `src/agent/cyber-security/PayloadEngineAgent/`.

