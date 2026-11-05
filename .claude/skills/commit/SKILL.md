---
name: commit
description: Use when the user asks to commit ("commit", "commit this", "commit these changes", /commit), or before writing any git commit message.
---

# Git Commit

Create well-formatted git commits following conventional commit standards.

## Project profile

Read [references/project.md](references/project.md) first. It gives this repository's module scopes, the scopes
that are not modules, the files that make a commit `ci`, and the habits of its history to avoid. Its match check
names the repository it describes: when the check fails in the repository you are in, the profile belongs to another
project. Rewrite `project.md` in place (git keeps the old one): fill its "This project" column from "How to find
it" and update the match check, show the user what you found, then commit.

## Behavior

1. Run `git status --short` and `git diff --staged`. Read the actual diff, not just the file names.
2. If nothing is staged, show the unstaged changes and ask which ones to stage. Never `git add -A` or `git add .` on the user's behalf; stage named paths only.
3. Generate a message following the rules below. Do not show it for approval first: commit directly.
4. Check `git diff --cached --stat` against what the user asked to commit. A bare `git commit` sweeps in everything already staged, so unstage what does not belong (`git restore --staged <paths>`) before committing, and restage it afterwards.
5. Commit with a heredoc so the body keeps its line breaks:
   ```bash
   git commit -F - <<'EOF'
   fix(payments): guard against an empty beneficiaries list in the picker
   EOF
   ```
6. Report the short hash and the stat line.

Never `--amend`, `--no-verify` or push unless the user asks.

## Commit format

```
<type>(<scope>): <description>

[optional body]
```

Messages are written in **English**, whatever the language of the diff or of the app's wording.

- No space before the colon, one space after: `fix(login): …`, not `fix(login) : …`.
- The description starts lowercase, in the imperative: `add`, `stop`, `show`, `keep`, not `Added`, `Adds`, `Display`.
- The body is separated from the title by a blank line and wrapped at ~72 characters.

## Types

`feat`, `fix`, `refactor`, `chore` and `docs` cover almost every commit. `test` is for commits that only add or
change tests, `ci` for the pipeline and release-automation files the profile lists. `perf` and `build` are available
when a change genuinely is one.

There is no `style` type, and no `design` type: a formatting-only change is `chore`, and a UI restyle is `refactor`
or `feat` depending on whether the result changes.

**A feature name is not a type.** `payments: fix crash` puts a scope in the type slot: write `fix(payments): …`.

## Scope

The scope is the **Gradle module** the change lands in, spelled exactly as in `settings.gradle(.kts)`, with its group
prefix (`:features:`, `:core:`, `:common:`…) dropped: `:features:payments` → `payments`, `:core:network` → `network`.
The profile lists the modules whose scope is not their name (a shared name prefix the project drops) and the scopes
that are not modules (`deps`, `app`, `release`…).

Keep the module's own casing and separators (`myAccounts`, `e_sim`, `stock_market`). Never invent variants: one
module written three ways (`esim`, `e-sim`, `e_sim`) makes `git log --grep` useless. When unsure, check the settings
file and the profile.

A sub-feature is not a scope: the QR decoding inside `payments` is `payments`, not `qr`; a consent screen of `flight`
is `flight`, not `flight consent`. A scope never contains a space.

**Several modules**: the scope of the module that carries the change. When two modules carry it equally, list both,
comma-separated, no space: `fix(payments,flight): …`. Never `+`, `, ` or ` + `. Three or more means the change is
repo-wide: omit the scope, or split the commit.

## Rules

**Title**: states precisely what the commit did, matching the diff rather than paraphrasing it. Avoid `optimize`,
`improve`, `update`, `tidy`, `refine`, `enhance`, `handle`: they name a direction, not a change. Under ~72
characters, with a scope and a verb, no trailing period. A commit doing two things puts the dominant one in the title
and the other in the body. No class names or SDK constants in the title (`TapAndPay.TOKEN_STATE_ACTIVE`): describe
the behaviour, and put identifiers in the body if they matter.

**Body**: optional, and usually not needed. Write one only when the change warrants it: an algorithm, a business
rule, a non-obvious constraint, or a bug whose cause is worth recording. Explain the why and the mechanism; never
restate the file list, which the diff already gives. A one-line fix takes no body.

A bulleted body listing what each file does is the failure mode to avoid: it repeats the diff and says nothing about
intent. Prefer prose that a reader six months later could not reconstruct from the code.

**Never add a `Co-Authored-By` trailer.**

**Where the *why* goes**: in the body, not the title. The title says what changed, precisely; the body says why it
had to. When a commit is obvious enough to need no body, its *why* simply goes unwritten; that is fine.

**Do not imitate the surrounding history.** Most histories predate these rules (`Update step navigation logic in
ProspectMainFragment`, followed by a bullet per file); the profile quotes this repository's typical ones. Reading
`git log` for a house style reproduces exactly what these rules forbid. The rules win.

## Examples

Typical one-liners, one per type:

```
feat(onboarding): add a liveness retry after a failed OCR step
fix(booking): guard against an empty rooms list in the rooms adapter
refactor(views): drop the stored Context from the recycler adapters
chore(deps): raise compileSdk and targetSdk to 36
chore(release): bump version to 4.1.1 (versionCode 539)
docs: document the XML to Compose migration strategy
```

A fix spanning two modules equally:

```
fix(payments,flight): keep Gson models from R8 renaming in release builds
```

A fix that needs no body:

```
fix(login): give the password field focus when the screen opens
```

A fix whose cause is worth recording:

```
fix(data): map server code 10000 to a prospect-already-created error

A retried onboarding initialisation returns 10000, which fell through to
the generic error branch and showed "service unavailable". The prospect
already existed, so the user had no way forward but to abandon
onboarding; the flow now resumes at the step the prospect stopped on.
```

A refactor whose motivation is not visible in the diff:

```
refactor(views): drop the stored Context from the recycler adapters

The adapters held the Activity Context in a field for the lifetime of
the list, so a rotation leaked the whole Activity. Binding now reads
the Context from itemView, which lives and dies with the row. The
deprecated Context constructors stay for the call sites not yet moved.
```

## Common mistakes

| Mistake                                                                 | Fix                                                                                 |
|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `git commit` with other files already staged                            | Pass the paths, or check `git diff --cached --stat` first                           |
| `booking: fix crash in rooms adapter`                                   | `fix(booking): guard against an empty rooms list`                                   |
| `Update price display logic in activation flow`                         | Name the change: `fix(cards): hide the price container when no price is returned`   |
| `fix(login) : fix menu in prospect case`                                | No space before the colon, and name the change: `fix(login): show the prospect menu` |
| `fix(esim + booking): …`, `fix(esim+flight): …`                         | `fix(e_sim,booking): …`: module names, comma, no space                              |
| `feat(password recovery): …`, `fix(qr): …`                              | The module: `feat(security): …`, `fix(payments): …`                                 |
| `fix(GPay): Display GPay added button and ignore TapAndPay.TOKEN_…`     | Lowercase, behaviour not constants, under 72 chars                                  |
| `feat: v1.2.0 — new screen, bottom nav, detail redesign`                | One change per commit; a release commit is `chore(release): …` and holds the version bump only |
| Body listing one bullet per changed file                                | Delete it, or replace with prose on the why                                         |
| Title carrying the motivation (`fix(x): align UX with retry semantics`) | Title = what changed; motivation moves to the body                                  |
| Amending after a hook failure                                           | The commit never happened: fix, restage, commit again                               |
| Subject line in another language than English                           | English, even when the diff changes wording in another language                     |
