---
name: chained-zero-day-agent
description: Chained zero-day exploitation simulation: multi-stage attack chains with AI-assisted optimization, vulnerability correlation, and chain viability scoring.
---
# /chained-zero-day-agent

Invoke the **ChainedZeroDayAgent** cyber-security agent (sources vendored under `src/agent/cyber-security/ChainedZeroDayAgent/`).

## Usage
```python
from agents.specialized.chained_zero_day import ChainedZeroDayAgent

agent = ChainedZeroDayAgent()

# Describe capabilities
info = agent.describe()

# Build a chain
chain = agent.build_chain(stages=[
    {"stage": 1, "type": "messaging_rce", "cve": "CVE-2019-8641"},
    {"stage": 2, "type": "kernel_lpe", "cve": "CVE-2019-8646"},
    {"stage": 3, "type": "sandbox_escape", "cve": "CVE-2019-8647"},
    {"stage": 4, "type": "covert_channel", "method": "dns_tunnel"}
])
chain_id = chain["chain_id"]

# Analyze chain viability
analysis = agent.analyze_chain(chain_id)

# Simulate chain execution
result = agent.simulate_chain(chain_id, target="192.168.1.100")

# List known real-world chains
chains = agent.list_chains()

# Get optimization suggestions
optimized = agent.optimize_chain(chain_id)

# Get CVE database
cves = agent.get_cves()
```

## Capabilities
- **Chain Building**: Construct multi-stage exploit chains from individual vulnerability components
- **Chain Analysis**: Analyze chain viability, dependencies, and success probability
- **Chain Simulation**: Simulate chain execution against targets (returns `{"status": "simulated"}`)
- **Chain Optimization**: AI-assisted suggestions for improving chain reliability and stealth
- **Real-World Chains**: Reference database of documented exploit chains (Pegasus, FORCEDENTRY, BLASTPASS)
- **CVE Database**: CVE lookup for chain building components
- **Dependency Analysis**: Map inter-stage dependencies and failure modes
- **Success Probability**: Calculate overall chain success probability from individual stage probabilities

Skill doc: `src/skill/cyber-agents/chained-zero-day-agent/SKILL.md`. Full sources: `src/agent/cyber-security/ChainedZeroDayAgent/`.

