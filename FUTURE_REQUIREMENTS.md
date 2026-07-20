# SmartXrayClient Future Requirements & Roadmap

This document outlines the planned improvements and new features for the SmartXrayClient project, serving as a guide for implementation.

## 1. Core Stability & UI Consistency
- **Dynamic Language Switching**: UI components react immediately to locale changes.
- **Robust Xray Core Lifecycle**: Improved error handling and state synchronization.

## 2. Advanced Scanner & Worker Infrastructure
- **Parallel Scanner Engine**: Uses `Coroutine Workers` with adjustable concurrency.
- **Streaming Results**: Healthy configs are added to the list in real-time during scanning.
- **Customizable Concurrency**: Settings for number of parallel workers.

## 3. Intelligent Subscription Management
- **Auto-Update Policy**:
    - Automatic update every 72 hours.
    - Forced update if older than 12 hours and no active configs.
    - Automatic disabling of subscriptions with zero active configs after update (Obsolete).
- **Control**: Toggle switch to enable/disable each subscription.

## 4. UI/UX Transformation
- **Expandable Lists**: Subscription groups and config lists use accordion-style expanding/collapsing.
- **Categorized Settings**: General, Routing, DNS, and Scan sections.
- **Fixed-IP Mode**: Option to choose a specific server for a stable IP while in Smart Mode.

## 5. Standardized Naming & Export (Production Policy)
- **Geo-IP Integration**: Automatic country detection based on server IP.
- **Naming Policy**: `[Emoji Flag] + [Country Name (FA/EN)] + [App Name]`.
- **Bulk Export**: Share the link or copy all healthy configs with standardized naming.

## 6. Implementation Phases
1. **Phase 1**: Infrastructure for Parallel Scanning & Real-time UI updates.
2. **Phase 2**: Redesign Settings & Config List UI (Expandable sections).
3. **Phase 3**: Implement Intelligent Update & Naming policies.
4. **Phase 4**: Final Verification & Production Readiness.
