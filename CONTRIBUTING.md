# Contributing to V2IR

Thank you for your interest in contributing to V2IR!

## Getting Started

1. Read the [README](README.md) to understand the project structure
2. Check [open issues](../../issues) for things to work on
3. For large changes, open an issue first to discuss the approach

## Development Setup

Follow the [Setup section in README](README.md#-setup) to get the project running locally.

## Code Style

- Follow existing Kotlin code style (MVVM + Clean Architecture)
- Use Hilt for dependency injection — no manual instantiation
- All new screens must use Compose + GlassCard components for UI consistency
- Coroutines for async work — no RxJava
- Add KDoc comments for public functions

## Commit Messages

Use conventional commits:
```
feat: add parallel scanner concurrency setting
fix: resolve DNS loop in XrayConfigBuilder
refactor: extract XrayConstants to separate file
docs: update README setup instructions
```

## Pull Request Checklist

- [ ] Code builds without errors (`./gradlew assembleDebug`)
- [ ] No new crashes or regressions
- [ ] UI changes follow Glassmorphism design system
- [ ] New logic includes error handling
- [ ] `PROJECT_DETAILS_FA.md` updated with a new LOG entry if applicable

## Architecture Rules

- **Never** put business logic in Composables
- **Never** access database directly from ViewModel — use Repository
- **Never** start Xray process outside of `XrayController`
- **Always** handle `null` and exception cases in `ConfigUriParser`

## Reporting Bugs

Include:
1. Android version and device model
2. Steps to reproduce
3. Expected vs actual behavior
4. Relevant logs (Settings → Logs screen)
