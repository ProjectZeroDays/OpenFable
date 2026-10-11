# BruteForceAgent — White Paper

## Overview
GPU-accelerated brute force and password cracking.

## Architecture
The BruteForceAgent agent implements a modular exploitation framework with:
- Vulnerability discovery
- Payload generation
- Exploitation orchestration
- Post-exploitation capabilities

## CVE Database
- Maintains a curated database of relevant CVEs
- Supports real-time CVE lookups
- Tracks exploit availability status

## Testing
Run tests with:
```bash
pytest bruteforceagent/test_bruteforceagent.py -v
```
