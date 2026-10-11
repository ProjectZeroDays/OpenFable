---
name: compose:google-mobile-ads-interstitial
hidden: true
description: google-mobile-ads-interstitial
source_platform: openclaw
---

# Google Mobile Ads SDK - Interstitial Ads

Interstitial ads show full-page ads for users on mobile apps. Interstitial ads
are designed to be placed between content and are best placed at natural app
transition points.

### Ad Placement Guidelines

**CRITICAL:** You MUST evaluate and apply the following Ad Placement Guidelines
before proceeding with any interstitial ad implementation.

*   **Determine Ad Placement**:
    *   [ ] **Identify the target file** where the ad should be placed. Ask if
        unsure.

## Workflow

1.  **Determine the user's platform**: Identify if the project is Android or
    iOS. If unclear, ask before proceeding.

2.  **Read the platform guide** for implementation details:
    -   Android: `references/android-interstitial.md`
    -   iOS: `references/ios-interstitial.md`

3.  **Follow these steps in order**:
    -   [ ] Load the ad
    -   [ ] Register for ad event callbacks
    -   [ ] Show the ad
    -   [ ] Verify the implementation
