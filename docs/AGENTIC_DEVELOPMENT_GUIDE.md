# Agentic App Development Guide

A practical guide for AI agents and users building software together from scratch. This process was refined while building [GhostChess](https://github.com/IanNorris/GhostChess) — a Kotlin Multiplatform chess simulator with 155 unit tests and 74 E2E tests, developed iteratively in small commits.

---

## Phase 0: Onboarding & Discovery

Before writing any code, establish shared context. The agent should ask these questions:

### 1. Developer Experience Level

Understanding the user's experience shapes every subsequent interaction:

| Level | Description | Agent behaviour |
|---|---|---|
| **None** | "You make the decisions for me" | Agent chooses tech stack, architecture, and patterns. Explains what was done and why in plain language. Minimises jargon. |
| **Some** | "I'd like to contribute to technical decisions" | Agent proposes options with trade-offs, asks for preferences on key decisions. Explains technical concepts briefly when introducing them. |
| **Expert developer, technology novice** | "I know what I'm doing, but this tech stack is new to me" | Agent makes idiomatic choices for the stack and explains stack-specific patterns. User drives architecture decisions. |
| **Expert developer, technology expert** | "I know exactly what I want" | Agent offers frequent design and implementation choices. User drives most decisions. Agent focuses on execution speed. |

### 2. Project Vision

Ask enough to start, not enough to over-plan:

- **What are you building?** (one sentence)
- **Who is it for?** (yourself, a team, public users)
- **What's the most important feature?** (the one thing it must do)
- **Any platform constraints?** (mobile, web, desktop, CLI)
- **Any technology preferences or constraints?** (languages, frameworks, hosting)

### 3. Working Style

- **How involved do you want to be?** (approve each step vs. check in periodically)
- **How do you feel about tests?** (TDD, test-after, minimal tests, no tests)
- **Deployment preferences?** (manual builds, CI/CD, app stores)

> **Don't over-plan.** Gather just enough to make the first commit. Details emerge as you build.

---

## Phase 1: Repository & Infrastructure First

Set up the project skeleton before writing any feature code. This is the foundation everything else builds on.

### 1.1 Create a Remote Repository

Do this immediately, even before writing code:

```
1. Create remote repo (GitHub/GitLab/etc.) with licence
2. Clone locally
3. Make an initial commit with just the project skeleton
4. Push — you now have a safety net
```

**Why remote first?** It forces you to think about the project as a real, persistent thing. It enables collaboration. It gives you a backup from minute one. And it means every subsequent change is a small, pushable delta.

### 1.2 Project Scaffold

Set up the build system and directory structure:

```
1. Initialise the build tool (gradle, npm, cargo, etc.)
2. Create the source directory structure
3. Add a .gitignore
4. Verify it builds (even if there's no code yet)
5. Commit: "Initial project scaffold"
```

### 1.3 Test Framework

Set up testing infrastructure before writing any feature code:

```
1. Add test dependencies
2. Create a trivial test that passes (e.g., "true is true")
3. Verify the test runner works
4. Commit: "Add test framework"
```

**This is non-negotiable.** If you can't run tests, you can't verify anything. Setting this up later always costs more than setting it up now.

### 1.4 E2E Test Infrastructure (if applicable)

For apps with a UI, set up end-to-end testing early:

```
1. Choose an E2E framework (Playwright, Cypress, Detox, etc.)
2. Write one test: "app loads and shows the main screen"
3. Verify it runs in CI-compatible mode (headless)
4. Commit: "Add E2E test framework"
```

> **Lesson learned:** Some UI frameworks render to canvas (e.g., Compose for Web/Wasm), making DOM-based E2E testing impossible. If E2E testing is important, verify your rendering target is testable *before* building the whole UI. You may need a separate testable frontend.

---

## Phase 2: Core Logic with Tests

Build the domain model and business logic with tests, before any UI.

### The TDD Loop

```
1. Write a failing test for the smallest useful behaviour
2. Write the minimum code to make it pass
3. Commit
4. Repeat
```

### Guidelines

- **Test the core, not the framework.** Chess rules, not button clicks. API logic, not HTTP routing.
- **Keep tests fast.** Unit tests should run in seconds, not minutes.
- **Use deterministic inputs.** Avoid randomness in tests. If your system has randomness, make it seedable.
- **Name tests descriptively.** `pawnCanMoveTwoSquaresFromStartingPosition` not `test7`.
- **Commit after each meaningful test group.** "Add pawn movement tests and implementation" is a good commit.

### Milestone Check

Before moving to UI, you should have:
- [ ] Core domain model with tests
- [ ] All business rules encoded in tests
- [ ] All tests passing
- [ ] Everything committed and pushed

---

## Phase 3: UI Development

Build the user interface on top of the tested core.

### 3.1 Start Minimal

```
1. Render the most basic version of the main screen
2. Connect it to the core logic
3. Take a screenshot or verify visually
4. Commit
```

### 3.2 Visual Verification

Screenshots are invaluable for agent-driven development:

```
1. Write automated screenshot tests early
2. Review screenshots after each UI change
3. Screenshots catch issues code review can't:
   - Layout problems (elements overlapping, wrong sizes)
   - Missing content (emoji not rendering, fonts not loading)
   - Platform-specific rendering differences
```

### 3.3 UI Iteration Pattern

Each UI change follows this cycle:

```
1. Identify the problem (screenshot, user report, or test failure)
2. Fix it with the smallest possible change
3. Run existing tests to verify nothing broke
4. Add a test if the bug could recur
5. Take new screenshots
6. Commit and push
```

---

## Phase 4: Integration & Features

Layer features on top of the working core + UI.

### The Feature Cycle

```
1. User describes the feature
2. Agent asks clarifying questions (scope, edge cases, priorities)
3. Write tests for the expected behaviour
4. Implement the feature
5. Run all tests (unit + E2E)
6. Build deployment artifact (APK, binary, etc.)
7. Commit with descriptive message
8. Push
```

### Keep Changes Small

**Every commit should be deployable.** If a feature takes multiple commits, each intermediate state should still work. This means:

- No half-implemented features in a commit
- No broken tests in a commit
- No "WIP" commits on the main branch

### Bug Fix Pattern

When a bug is reported:

```
1. Write a test that reproduces the bug (it should fail)
2. Fix the bug with the minimum change
3. Verify the test passes
4. Run ALL tests to check for regressions
5. Commit: "Fix [bug description]" with the test + fix together
```

This ensures the bug can never silently return.

---

## Phase 5: Polish & Deployment

### 5.1 Build Artifacts Regularly

Don't wait until "the end" to build:

```
- Build the deployable artifact after each feature
- Test the artifact, not just the source code
- Keep a current artifact in the repo or CI
```

### 5.2 Documentation

Write documentation when the feature is fresh:

```
- README.md: What it is, how to build, how to test
- Screenshots: Auto-generated from test suite
- Architecture notes: Only if the codebase is non-obvious
```

### 5.3 Keep Android/iOS/Web in Sync

For multi-platform projects:

```
- Implement features in the shared/core layer first
- Update all platform UIs together (or document the gap)
- Rebuild all platform artifacts after shared changes
```

---

## Anti-Patterns to Avoid

| ❌ Don't | ✅ Do |
|---|---|
| Plan everything upfront | Plan enough to start, discover the rest |
| Write all tests at the end | Write tests before or alongside code |
| Make large commits with multiple changes | One logical change per commit |
| Build the UI before the core logic | Core logic → tests → UI |
| Skip the test framework setup | Set it up before writing any feature |
| Wait until the end to build artifacts | Build after each feature |
| Fix unrelated bugs while implementing a feature | Log them, fix them in a separate commit |
| Ignore screenshot/visual verification | Automate screenshots early |
| Over-engineer early | Start simple, refactor when needed |
| Push without running tests | Always: test → commit → push |

---

## Communication Protocol (Agent ↔ User)

### When to Ask vs. When to Decide

**Ask the user when:**
- Multiple valid approaches exist and the choice affects the user's experience
- The scope is ambiguous ("should this also handle X?")
- A design decision is hard to reverse later
- The user's experience level is "Some" or higher

**Decide autonomously when:**
- The user's experience level is "None"
- There's a clearly idiomatic choice for the tech stack
- The decision is easily reversible
- It's an implementation detail that doesn't affect the user

### After Every Change

Provide a brief summary:
- What changed (1-2 sentences)
- Test results (X unit, Y E2E, all passing)
- Artifact status (APK rebuilt, binary updated, etc.)
- What's next (if continuing)

### When Things Break

Be transparent:
- Show the error
- Explain what went wrong (briefly)
- Explain the fix
- Show that tests pass after the fix

---

## Checklist: New Project Kickoff

```
[ ] Onboarding questions answered (experience, vision, style)
[ ] Remote repository created with licence
[ ] Project scaffold builds
[ ] Test framework runs a trivial test
[ ] E2E framework runs (if applicable)
[ ] Core logic has tests
[ ] UI renders and is visually verified
[ ] Screenshot tests automated
[ ] Deployment artifact builds
[ ] README exists with build/test instructions
[ ] All tests pass
[ ] Everything pushed to remote
```
