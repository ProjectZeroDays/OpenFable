---
name: deserialization-agent
description: Deserialization vulnerability exploitation
---
# /deserialization-agent

Invoke the **DeserializationAgent** cyber-security agent (sources vendored under `src/agent/cyber-security/DeserializationAgent/`).

## Usage
```python
from deserializationagent import DeserializationAgent
agent = DeserializationAgent()
result = agent.describe()
```

## Capabilities
- Vulnerability scanning and exploitation
- Payload generation and delivery
- Post-exploitation and persistence
- Evasion techniques

Skill doc: `src/skill/cyber-agents/deserialization-agent/SKILL.md`. Full sources: `src/agent/cyber-security/DeserializationAgent/`.

