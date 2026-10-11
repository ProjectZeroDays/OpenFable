---
name: compose:google-mobile-ads-rewarded
hidden: true
description: google-mobile-ads-rewarded
source_platform: openclaw
---

# Google Mobile Ads SDK - Rewarded Ads

Rewarded ads reward users with in-app items for interacting with full-screen
ads. Rewarded ads are served after a user explicitly opts in to view a rewarded
ad.

### Ad Placement Guidelines

**CRITICAL:** You MUST evaluate and apply the following Ad Placement Guidelines
before proceeding with any rewarded ad implementation.

*   **Determine Ad Placement**:
    *   [ ] **Identify the target file** where the ad should be placed. Ask if
        unsure.

## Workflow

1.  **Determine the user's platform**: Identify if the project is Android or
    iOS. If unclear, ask before proceeding.

2.  **Read the platform guide** for implementation details:
    -   Android: `references/android-rewarded.md`
    -   iOS: `references/ios-rewarded.md`

3.  **Follow these steps in order**:
    -   [ ] Load the ad
    -   [ ] Register for ad event callbacks
    -   [ ] Add an opt-in UI element
    -   [ ] Show the ad
    -   [ ] Verify the implementation