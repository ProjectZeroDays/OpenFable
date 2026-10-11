---
name: compose:autonomous-blue-ops
hidden: true
description: autonomous-blue-ops
source_platform: openclaw
---

# Autonomous Blue Ops

This skill enables the agent to act as a defender and quality assurance engineer for security.

## Hardening Workflows
- **Surface Reduction**: Identify and disable unnecessary services, ports, and protocols.
- **Configuration Hardening**: Apply CIS benchmarks and STIGs to OS and application configs.
- **Patch Management**: Automatically identify and deploy critical security updates.

## Purple Team Testing
- **Attack Simulation**: Use the `autonomous-red-teaming` skill to launch controlled attacks.
- **Detection Gap Analysis**: Compare the attack logs with SIEM/EDR alerts to find "blind spots."
- **Iterative Hardening**: Patch the gap $\rightarrow$ Re-test $\rightarrow$ Verify.
