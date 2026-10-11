---
name: compose:cloud-optimizer
hidden: true
description: cloud-optimizer
source_platform: openclaw
---

# Cloud Resource Optimizer

This skill prevents budget overruns and ensures high availability.

## Optimization Workflows
- **Usage Tracking**: Monitor CPU/RAM/Bandwidth usage for all C2 nodes.
- **Cost Projection**: Predict monthly spending based on current usage patterns.
- **Auto-Scaling**: Spin down unused nodes or switch to cheaper spot instances during low-activity periods.
- **Platform Integration**: Bridge to AWS Cost Explorer, GCP Billing, and DigitalOcean API for real-time data.

## Alerts & Warnings
- **Budget Thresholds**: Warn the user when spending reaches 80% of the monthly budget.
- **Efficiency Gaps**: Identify underutilized nodes that can be consolidated.
