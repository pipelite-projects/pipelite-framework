# Contributing to Pipelite Framework

Thanks for your interest in contributing. This document describes how to propose a change,
what's expected of it, and the conventions this repository already follows.

By participating, you are expected to uphold the project's [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

Pipelite is a multi-module Maven project (Java 17) still on its way to `1.0.0`. The public
API/SPI surface can still change between minor versions until then.

- **Found a bug?** Open an issue using the *Bug report* template.
- **Want a new feature or a behavior change?** Open an issue using the *Feature request* template
  first, before writing code — this is a design discussion as much as a code one, and a maintainer
  may have context (an existing issue, a deliberate trade-off, a planned direction) that changes
  the approach. For anything beyond a trivial fix, wait for that discussion to converge before
  opening a pull request.
- **Just a question?** Open an issue, or comment on an existing one — there's no separate forum
  yet.

## Making a change

1. **Branch from `main`**, named `bugfix/issue-<N>-<short-title>` or
   `feature/issue-<N>-<short-title>` (hyphens only, a few significant words from the issue title) —
   for example `bugfix/issue-123-lockedfilestore-atomic-write`. If you have push access, GitHub's
   own "create a branch" link on the issue (or `gh issue develop <N>`) does this consistently; a
   fork-based contributor without push access just names their branch the same way.
2. **Keep the change scoped to the issue.** A bug fix doesn't need surrounding refactoring; a new
   feature doesn't need to fix unrelated things it happens to pass by. Open a separate issue for
   anything else you notice along the way.
3. **Add a license header** to every new source file — see `license_header.txt` at the repository
   root for the exact text; `mvn verify` fails the build if it's missing or stale
   (`license-maven-plugin`, `checkstyle-suppressions.xml` lists the rare, already-approved
   exceptions).
4. **Add or update tests** for whatever the change actually touches. This codebase leans on real
   behavioral tests over mocked-out ones — prefer exercising the actual class/flow being changed
   over stubbing its collaborators.
5. **Verify before opening the pull request**:
   ```bash
   mvn clean verify
   ```
   This is exactly what CI runs (`.github/workflows/pr-verification.yml`) — a full build plus the
   entire test suite across every module. There's a Checkstyle configuration
   (`checkstyle-suppressions.xml`) but it isn't enforced in CI yet (pre-existing violations are
   still being worked through), so `mvn checkstyle:check` failing today isn't necessarily a blocker
   — matching the surrounding code's style still is.
6. **Write a commit message that explains why, not just what.** This repository's own history is
   the best reference — the short form is a `[ModuleTag] Imperative summary (#issue)`, e.g.:
   ```
   [Core] Roll back already-started services when onContextStarted() fails (#54)
   [ChannelAdapter] FileProducer: normalize the target path and support an opt-in write-directory containment check (#60)
   ```
   `ModuleTag` is whichever module/area the change is mostly about (`Core`, `Common`,
   `ChannelAdapter`, `Spi`, `Dsl`, ...). The body, when there's one, is for the reasoning and
   trade-offs a diff alone doesn't show — a future reader (including you) should be able to tell
   *why* a change was made without archaeology.
7. **Open the pull request** against `main`, filling in the PR template (`.github/pull_request_template.md`)
   — link the issue it closes, describe how you tested it. Keep it focused: a pull request is
   easiest to review when it does one thing.

## What to expect in review

A maintainer will review for correctness, scope, and fit with the framework's existing design —
not just "does it work." Expect follow-up questions on design choices, especially anything that
changes public API/SPI shape. Review feedback is about the code, never about you — see the Code of
Conduct.

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE.txt), the same license covering the rest of the project.
