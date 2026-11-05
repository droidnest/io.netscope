---
name: android-dagger-to-hilt
description: >
  Use when migrating an Android project from legacy Dagger 2 (components,
  injectors, custom scopes, Dagger ViewModel factories, dagger.android) to Hilt
  one feature at a time, bootstrapping Hilt, bridging shared legacy bindings,
  fixing Hilt MissingBinding / DuplicateBinding / InstallIn errors in a hybrid
  graph, or removing the remaining Dagger infrastructure.
---
# Android Dagger 2 → Hilt Incremental Migration Skill

## Purpose

Migrate an Android application incrementally from legacy Dagger 2 to Hilt.

The migration is feature-by-feature.

The final objective is NOT merely to make an individual feature compile with Hilt.

The final objective is:

```text
Legacy Dagger surface = 0
Temporary Dagger → Hilt bridges = 0
Feature Dagger components = 0
Feature Injector objects = 0
Obsolete custom Dagger scopes = 0
Legacy Dagger ViewModel infrastructure = 0
Legacy Dagger dependencies = 0
```

when the complete application migration is finished.

Each migration must reduce the remaining legacy Dagger surface without breaking non-migrated features.

## Project profile

[references/project.md](references/project.md) holds what is specific to this repository: its legacy
component graph and injectors, the shared base classes that perform legacy injection, the migration
ledger location, the pilot precedents, the build tasks, and the pitfalls already met here. Read it
during Phase 0, as evidence to verify against current source, never as a substitute for it (Absolute
rule 1). Its match check names the repository it describes: when the check fails in the repository
you are in, rewrite `project.md` in place during Phase 0 (git keeps the old one), filling the "This
project" column from "How to find it" and updating the match check, and show it to the user with the
Phase 0 report. Phase 0 is analysis: this file is the only one you write before approval.

---

# Core principle

Migrate ownership gradually:

```text
LEGACY

Application
    │
    ▼
BaseComponent                    (names: see the profile)
    │
    ├── shared component (Common, Security, Analytics…)
    ├── shared component …
    ├── FeatureComponent A
    ├── FeatureComponent B
    └── ...


TRANSITION

@HiltAndroidApp
    │
    ▼
Hilt component hierarchy
    │
    ├── migrated feature
    ├── migrated feature
    └── migrated feature
             ▲
             │
      temporary bridges
             │
       Legacy Dagger
             │
             ├── Base
             ├── shared components
             └── remaining features


FINAL

@HiltAndroidApp
    │
    ▼
Hilt component hierarchy
    │
    ├── application bindings
    ├── activities
    ├── fragments
    ├── ViewModels
    ├── services
    └── all features

Legacy Dagger removed.
Temporary bridges removed.
```

---

# Absolute rules

## 1. Repository is the source of truth

Never assume a feature follows the same DI pattern as a previously migrated feature.

Before migrating target module `X`, inspect its actual:

- Component
- Injector
- Scope
- Modules
- component dependencies
- Android entry points
- ViewModels
- repositories
- use cases
- services
- factories
- runtime parameters
- parent classes
- shared dependencies
- navigation ownership

Patterns discovered in another feature are evidence, not truth.

**Zero-context execution rule:** Every session begins without memory of previous sessions. Before each migration or gate:

1. Run Phase 0 unconditionally. Do not assume migration state from prior conversations.
2. Read the current source of every file you intend to modify. Do not assume its contents match a previous session's read.
3. Read the current migration ledger (if it exists) to discover bridges and escape hatches already in place.
4. If a migration was partially completed in a prior session, recover the current state from the repository before resuming.

Saying "as established previously" or "continuing from last time" is not a substitute for reading current source. A prior session may have modified files you are about to modify again. Stale assumptions cause duplicate changes, missed removals, and incorrect bridge classifications.

---

## 2. Analysis before implementation

Every feature migration begins with analysis.

Do not modify files during the analysis phase.

Produce the migration plan and wait for explicit user approval.

Only implement after approval.

---

## 3. Never commit, push, merge, deploy, publish, or release without explicit user approval

Compilation and local tests are allowed after implementation approval.

Remote repository actions require separate explicit approval.

---

## 4. Preserve behavior before improving architecture

A DI migration is not permission to perform unrelated refactoring.

Prefer:

```text
existing behavior
+
new DI ownership
```

over:

```text
DI migration
+
architecture cleanup
+
lifecycle redesign
+
API cleanup
+
unrelated modernization
```

Record desirable cleanup as follow-up work unless required for Hilt migration.

**Exception:** When the pre-migration DI mechanism gives an actual Android ViewModel incorrect lifecycle ownership — for example, a class that extends ViewModel or BaseViewModel but is constructed directly by Dagger instead of being obtained from a ViewModelStore — the migration may correct that ownership.

Any resulting lifecycle change, such as surviving configuration changes after moving to @HiltViewModel + ViewModelProvider ownership, must be documented explicitly and approved. Treat this as a deliberate migration behavior change, not unrelated cleanup.

**Second exception — secondary ViewModel elimination:** removing an entry point's direct dependency on ViewModels owned by other modules (Phase 10b) is an architecture refactor that exists mainly to make Hilt ownership possible without touching shared modules. It is allowed only when the user requests it or approves it in the plan, one secondary ViewModel per gate, with behavior preserved. It is never started implicitly.

---

## 5. Preserve actual lifetime, not annotation names

Never translate:

```text
@FeatureScope → @Singleton
```

without inspecting runtime component construction.

Determine how the legacy component is actually created and cached.

Example:

```text
@FeatureScope
+
DaggerFeatureComponent.builder().build()
on every inject()
```

means the effective lifetime may only be one injection/component instance.
A scoped binding inside a component rebuilt on every `inject()` has per-injection effective lifetime, not per-session lifetime.

Do not infer application lifetime from the custom scope annotation.

Map the ACTUAL runtime lifetime to the appropriate Hilt ownership.

When uncertain, prefer unscoped bindings during migration.

Installing an unscoped provider in `SingletonComponent` does NOT automatically make the provided object a singleton. Never add `@Singleton` solely because the module is installed in `SingletonComponent`.

**Intra-component vs cross-inject-call identity:**

`@FeatureScope` on a binding inside a Dagger component guarantees one instance **per component instance**, not one instance per application or session.

If the Injector rebuilds the component on every `inject()` call:

```text
fun inject(target: SomeFragment) {
    buildComponent().inject(target)  // new component = new scope cycle
}
```

then two objects constructed from two separate `inject()` calls to the same Injector will NOT share a `@FeatureScope`-scoped instance, even though both are `@FeatureScope`.

**Performing the consumer topology audit before removing a `@FeatureScope` annotation:**

For every class whose `@FeatureScope` you plan to remove:

1. Enumerate all objects whose dependency graph is resolved through the SAME legacy component instance.
2. Determine whether any two of those objects depend on the same `@FeatureScope`-scoped binding — regardless of whether they were injected via the same `inject()` method call or different ones on the same component instance.
3. If multiple objects resolved through the same component instance depend on the same `@FeatureScope` binding, they share one instance.
4. If those objects require shared identity (e.g. a shared cache, a shared state holder), removing `@FeatureScope` changes observable behavior.
5. Only proceed with unscoped if: (a) no two objects resolved through the same component instance both require shared identity via this binding, or (b) intra-component sharing is incidental (no correctness dependency on shared identity).

Never remove a scope annotation solely because the component had a short lifetime. Even a short-lived component may legitimately share one instance across multiple objects constructed in that component's graph.

---

## 6. Exact binding key identity

Before removing or replacing any binding, compare the exact Dagger key:

- type
- qualifier
- generic type parameter
- multibinding key where applicable

These are distinct bindings even though the underlying type is the same:

```text
Context                         ← unqualified
@ApplicationContext Context     ← Hilt-qualified
@ActivityContext Context        ← Hilt-qualified
```

Never remove an unqualified binding merely because Hilt provides a qualified binding of the same underlying type.

When ownership is ambiguous, inspect the generated component (e.g. `DaggerApp_HiltComponents_SingletonC.java`) to confirm exactly which factory method provides which binding key.

---

## 7. Compiler contradiction rule

If implementation evidence contradicts an approved plan:

STOP.

Report:

- the contradiction
- exact source or generated evidence
- blast radius if the approved plan is followed anyway
- smallest safe alternatives

Do not blindly implement the approved plan when evidence shows it is incorrect.

Require explicit approval if the safe resolution changes approved scope.

**Typical case:** an approved plan removes an unqualified `Context` bridge because Hilt provides `@ApplicationContext Context`; inspection of the generated Hilt graph shows a shared base Fragment still consuming the unqualified key through a members-injection method (`injectMContext`). The plan is updated before implementation. (The profile records this repository's precedents.)

**Gate approval is temporal:**

User approval of a migration plan is valid at the moment it is given, against the source state that existed at approval time.

If between approval and implementation:

- files have changed (e.g. another session modified source)
- a prior gate's implementation revealed new information about the graph
- a compilation failure surfaced a dependency that was not visible during analysis

then the approved plan may no longer be safe to implement unchanged.

Do not treat approval as permanent authorization to proceed regardless of what compilation or source inspection reveals. New evidence that contradicts the approved plan is a STOP condition — report the contradiction and require updated approval before continuing.

---

# Migration modes

Before working on the target module, determine repository migration state.

## Mode A — INITIAL_BOOTSTRAP

Use when Hilt has not yet been established at Application level.

Typical indicators:

- no `@HiltAndroidApp`
- Hilt Gradle plugin absent
- no Hilt root component

Responsibilities:

1. establish Hilt
2. preserve existing Dagger root graph
3. establish temporary interoperability
4. migrate the selected pilot feature

Do this incrementally with compile gates.

---

## Mode B — FEATURE_MIGRATION

Use after Hilt bootstrap exists.

Responsibilities:

1. inspect existing Hilt bindings
2. inspect existing temporary bridges
3. reuse them where appropriate
4. migrate the selected feature
5. add only the minimum additional bridge surface
6. remove the selected feature's obsolete Dagger infrastructure
7. update migration state

Do NOT create duplicate per-feature bridges for bindings Hilt already owns or exposes.

---

## Mode C — SHARED_INFRASTRUCTURE_MIGRATION

Use when migrating shared legacy graphs such as:

- the base/root component
- shared components of `common`/`core` modules (the profile lists this repository's)
- other shared components

This requires repository-wide consumer analysis.

Before migrating a shared component:

1. enumerate every remaining legacy consumer
2. enumerate migrated Hilt consumers
3. determine whether Hilt can become authoritative
4. update/remove temporary bridges
5. preserve non-migrated consumers if any remain

Never remove a shared Dagger component while legacy features still require it.

---

## Mode D — FINAL_DAGGER_REMOVAL

Use only after all consumers have migrated.

Verify repository-wide that no legitimate legacy Dagger usage remains.

Then remove:

- Dagger components
- subcomponents
- injectors
- component providers
- custom scopes that are obsolete
- Dagger ViewModel factories/multibindings
- Dagger Android infrastructure
- Dagger Gradle dependencies
- annotation processors
- convention-plugin Dagger configuration

Do not remove dependencies based on assumptions.

Prove zero consumers first.

---

# Phase 0 — Determine migration state and git baseline

Before target-feature analysis, perform both the repository state inspection AND the git baseline inspection. These are mandatory in parallel.

## Repository state inspection

Inspect:

- Application class
- root DI setup
- Gradle version
- AGP version
- Kotlin version
- JVM version
- Dagger version
- Hilt version if present
- kapt/KSP usage
- convention plugins
- existing Hilt modules
- existing temporary bridge modules
- migration ledger if present

Report:

```text
Migration mode:
Hilt bootstrap status:
Existing migrated modules:
Existing legacy shared components:
Existing bridges:
Target module:
```

## Git baseline inspection (mandatory)

**Never skip this. Never conclude that an artifact "never existed" merely because it is absent from the current working tree.**

Run:

```bash
git status --short
git diff --stat HEAD
```

Identify every changed, added, deleted, or renamed file related to:

- the target feature
- Hilt bootstrap files
- bridge modules
- host Activities
- shared base classes in the inheritance chain

For each deleted or modified file relevant to migration, recover its HEAD state:

```bash
git show HEAD:<path>
```

Classify every legacy artifact using provenance categories:

| Classification | Meaning |
|---|---|
| `EXISTED_IN_HEAD_AND_DELETED_BY_MIGRATION` | File was in HEAD, deleted in working tree by migration |
| `EXISTED_HISTORICALLY_BUT_NOT_AT_MIGRATION_START` | File existed in earlier commits but not at HEAD |
| `RENAMED_OR_REPLACED` | File was replaced by a differently-named equivalent |
| `NEVER_EXISTED_IN_RELEVANT_HISTORY` | No evidence in HEAD or relevant git history |

Generated DEX files, class files, and build-cache artifacts are **not** source-of-truth for provenance. A DEX in `build/` does not prove a source file exists or existed.

Distinguish clearly:

- current filesystem state (working tree)
- migration baseline (HEAD state of target feature)
- historical state (prior commits)
- generated/build-cache artifacts (not authoritative)

---

# Phase 1 — Discover target feature

Given target module `X`, find all relevant DI infrastructure.

Search for:

```text
XComponent
XSubcomponent
XInjector
XScope
XModule
DaggerX...
```

but do not rely only on naming conventions.

Also search:

```text
@Component
@Subcomponent
@Module
@Provides
@Binds
@Inject
@ContributesAndroidInjector
@IntoMap
@ViewModelKey
```

within the target feature.

Identify:

- component interface
- component dependencies
- builder/factory
- `@BindsInstance`
- modules
- custom scopes
- Injector objects
- component-provider interfaces
- exposed dependency methods
- injection sites

**If git baseline shows that artifacts existed at HEAD but are deleted in the working tree:** recover and document them. Do not proceed as if they never existed.

---

# Phase 2 — Build DI inventory

Enumerate every dependency consumed by the feature.

Include:

- Fragment fields
- Activity fields
- DialogFragment fields
- Service fields
- Worker fields
- BroadcastReceiver fields
- ViewModels
- repositories
- use cases
- data sources
- Retrofit services
- adapters
- mappers
- analytics
- routers
- session objects
- preferences
- caches
- factories

Trace dependencies recursively until reaching an already-resolved Hilt binding or a legacy-owned leaf.

Produce:

| Binding | Consumer | Current owner | Current provider | Actual lifetime | Proposed Hilt source |
|---|---|---|---|---|---|

---

# Phase 3 — Classify bindings

Every binding must belong to one category.

## Category A — Hilt constructible

Examples:

```kotlin
class Repository @Inject constructor(...)
```

or equivalent constructor-injectable graph.

Action:

Let Hilt construct it.

Do NOT bridge it merely because legacy Dagger previously constructed it.

---

## Category B — Feature module binding

Examples:

- Retrofit interface
- interface implementation binding
- factory requiring explicit construction
- feature-specific configuration

Action:

Migrate the feature's Dagger module to an appropriate Hilt module.

Example:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object FeatureModule {

    @Provides
    fun provideFeatureService(
        retrofit: Retrofit          // the project's Retrofit binding (profile)
    ): FeatureService =
        retrofit.create(FeatureService::class.java)
}
```

Scope only if existing lifetime semantics justify it.

---

## Category C — Legacy shared binding

Binding remains owned by legacy Dagger during incremental migration.

Action:

Use a temporary legacy → Hilt bridge.

Preferred direction:

```text
Legacy Dagger
      │
      ▼
low-level shared binding
      │
      ▼
Hilt @Provides bridge
      │
      ▼
Hilt constructs feature graph
```

Avoid:

```text
Legacy Dagger
      │
      ▼
preconstructed feature ViewModel
      │
      ▼
Hilt
```

Bridge at the lowest practical legacy ownership boundary.

---

## Category D — Hilt built-in Android binding

Examples:

- application Context
- Activity
- Fragment
- SavedStateHandle
- Application

Use Hilt's built-in bindings and qualifiers.

Do NOT bridge `Context` from legacy Dagger if `@ApplicationContext Context` is sufficient.

**Important:** Unqualified `Context` and `@ApplicationContext Context` are distinct binding keys. If consumers inject unqualified `Context`, a bridge providing unqualified `Context` may be required even though Hilt natively provides `@ApplicationContext Context`. Confirm by searching all `@Inject lateinit var` fields of type `Context` (without qualifier) in the affected graph.

---

## Category E — Runtime-bound dependency

Examples:

- runtime IDs
- navigation arguments
- account identifiers
- dynamically selected environment/configuration
- builder `@BindsInstance`
- values passed during feature component construction

Do not blindly move these into SingletonComponent.

Determine appropriate solution:

- SavedStateHandle
- Assisted Injection
- runtime method parameter
- Activity/Fragment scoped binding
- explicit factory
- another Hilt component

If lifetime/ownership cannot be preserved safely:

STOP and report a blocker.

---

## Category F — Existing Hilt binding/bridge

Before creating a new bridge, search the entire repository for an existing Hilt binding for the same type **and qualifier**.

If one exists:

reuse it.

Never create:

```text
FeatureALegacyCommonBridge
FeatureBLegacyCommonBridge
FeatureCLegacyCommonBridge
```

for the same underlying shared dependency.

Temporary bridges are application migration infrastructure, not permanent feature architecture.

If an existing feature-local bridge becomes useful to multiple migrated features, re-evaluate canonical ownership before duplicating it. Promote rather than copy — see Bridge ownership promotion rule.

---

# Phase 4 — Inspect actual component lifetime

For every legacy component involved, determine:

- where it is built
- how often it is built
- whether it is cached
- cache owner
- whether recreation occurs per Fragment
- per Activity
- per navigation flow
- per session
- per Application
- whether component dependency instances are reused

Produce:

| Component | Declared scope | Construction location | Cached? | Actual lifetime |
|---|---|---|---|---|

Use this table to determine Hilt scope.

Never map scopes by annotation name alone. A `@FeatureScope` on a component rebuilt on every `inject()` call has per-injection effective lifetime, not feature-session lifetime.

**Scope removal requires consumer topology audit:**

Even when the per-component effective lifetime is short, `@FeatureScope` may still provide meaningful intra-component sharing. Before removing any scope annotation, audit whether multiple objects in the SAME component instance's construction graph depend on the scoped class as a shared identity. See the consumer topology audit in Absolute rule 5.

---

# Phase 5 — ViewModel audit

ViewModels require special handling.

**First verify:** Does the class actually extend `ViewModel` or `BaseViewModel`? Dagger-created objects injected as `@Inject lateinit var viewModel: Foo` are NOT Android ViewModels unless they extend `ViewModel`. Treat them accordingly — they do not survive rotation.

For every class named or used as a ViewModel, determine:

- extends ViewModel/BaseViewModel?
- current constructor?
- `@Inject constructor`?
- field injection?
- created directly by Dagger?
- created by ViewModelProvider?
- DaggerViewModelFactory?
- `viewModels()`?
- `activityViewModels()`?
- `navGraphViewModels()`?
- manually stored Fragment field?
- parent-fragment reference?
- shared between screens?
- current ViewModelStoreOwner?

Produce:

| ViewModel | Current creation | Current owner | Shared? | Survives rotation today? | Proposed Hilt owner | Lifecycle change? |
|---|---|---|---|---|---|---|

Do NOT blindly replace an injected ViewModel with a Hilt delegate.

If moving to `@HiltViewModel` changes lifecycle semantics, explicitly document the change and require approval.

**Lifecycle change example:** A Dagger-created ViewModel injected via `@Inject lateinit var` is recreated on every `inject()` call and does not survive configuration changes. Moving to `@HiltViewModel` + `viewModels()` makes it survive rotation. This is usually desirable but must be stated explicitly, not hidden in a diff.

For each mutable state item determine whether it:

- should survive rotation
- should survive navigation
- should reset on new flow
- represents a one-shot event
- is view-only state

Do not add arbitrary `reset()` calls merely to emulate accidental old behavior.

### Cross-module ViewModels

While auditing, flag every ViewModel the feature's entry points use that is **defined outside the feature module**. They cannot be made `@HiltViewModel` without applying Hilt to modules that legacy features still share. Do not annotate them and do not wrap them in ad-hoc factories. List them in the plan and offer **Phase 10b — Secondary ViewModel elimination**; run it only if the user approves.

### ViewModel field visibility rule

Before converting `@Inject lateinit var viewModel: XViewModel` to a Hilt ViewModel delegate, search the repository for all accesses to that field by name — including from child fragment classes and parent-fragment references.

If any access is found outside the declaring class, the Hilt delegate property MUST have visibility at least as permissive as the original field.

`private val viewModel by viewModels()` is almost always wrong for any ViewModel accessed by a child or sibling class. Use `val viewModel by viewModels()` (package-internal) or `internal val` explicitly.

### Delegate selection rule

| Intended ViewModelStoreOwner | Correct delegate |
|---|---|
| This fragment | `viewModels()` |
| Host Activity | `activityViewModels()` |
| Navigation back-stack entry | `hiltNavGraphViewModels(navGraphId)` |

Do not use `activityViewModels()` unless activity lifetime is explicitly intended.
Do not use `hiltNavGraphViewModels()` unless nav-graph scoping is explicitly intended.

### ViewModel migration atomicity invariant

Conceptual migration phases (ViewModel → entry point annotation → legacy cleanup) do not automatically imply independently compilable gates.

**Before treating any ViewModel migration as atomic, determine whether a safe intermediate state exists:**

1. Examine whether Hilt's annotation processing accepts `@HiltViewModel` while the consuming entry point still carries a legacy `inject()` path.
2. If the consuming entry point is still listed in a legacy Dagger component's `inject()` method AND compiler evidence (kapt failure, `ViewModelValidationPlugin` rejection) proves that Hilt cannot validate the `@HiltViewModel` in that state — then the following must form one atomic gate:

   - `@HiltViewModel` on the ViewModel class
   - ViewModelStore delegate/provider migration on the consuming entry point
   - `@AndroidEntryPoint` on the consuming entry point
   - Removal of the conflicting legacy `inject()` declaration and call site

3. If repository or compiler evidence proves a safe intermediate state exists (e.g. the entry point is not yet in the legacy `inject()` path, or Hilt processing is proven not to reject the combination), separate gates remain allowed.

**Do not treat this as a universal 4-step atomic requirement.** Apply it only when the specific intermediate state is proven, or conservatively assumed from prior observed failures in this repository, to cause compiler rejection.

**Gate boundary determination:**
- Determine the intended gate boundary from repository and compiler evidence before applying edits.
- Never intentionally compile a partially-applied atomic gate. If steps 1–4 are determined to be atomic, apply all four before compiling.
- If a gate that was believed to be complete fails, STOP under the compiler contradiction rule (Absolute rule 7), report the failure, and propose the smallest revised atomic boundary. Do not resume without approval.

---

# Phase 6 — Android entry-point hierarchy audit

Before adding `@AndroidEntryPoint`, inspect inheritance.

For each target:

```text
ConcreteFragment
    ↓
FeatureBaseFragment
    ↓
SharedBaseFragment
    ↓
Fragment
```

Inspect:

- injection in constructors/init blocks
- `onAttach`
- `onCreate`
- `onCreateView`
- `onViewCreated`
- lifecycle coroutines
- parent Dagger members injection
- overlapping fields
- injection timing assumptions

Legacy parent-class Dagger injection may coexist temporarily with Hilt child injection if fields/ownership do not conflict.

Do not migrate a shared parent merely because one child feature migrates.

Abstract base classes should not automatically receive `@AndroidEntryPoint`.

Annotate actual Android entry points as required by Hilt and verify inherited member injection behavior.

### Legacy inject target ≠ Hilt entry point

The presence of a class in a legacy component's `inject()` method does NOT mean that class must become an `@AndroidEntryPoint`.

A class listed in `FeatureComponent.inject(SomeFragment)` may:

- Be a `DialogFragment` with `@Inject constructor()` and zero `@Inject lateinit var` fields — in which case the legacy `inject()` was a runtime no-op. No `@AndroidEntryPoint` needed.
- Be a class whose injectable fields are being removed as part of the migration — check the final state, not the pre-migration state.
- Have been listed in the component defensively or for historical reasons with no actual field injection.

**Before adding `@AndroidEntryPoint` to a class found in a legacy `inject()` list:**

1. Read the class source and its full superclass chain.
2. Determine whether Hilt Android ownership is actually required by checking all of:
   - Members/inherited injection requirements: `@Inject`-annotated fields across the class and every superclass.
   - Hilt ViewModel ownership requirements: does this class obtain `@HiltViewModel` instances via `viewModels()`, `activityViewModels()`, or similar delegates that require a Hilt-owned component as the ViewModelStoreOwner?
   - Android component hierarchy/descendant requirements: does Hilt require this class to be `@AndroidEntryPoint` because a child Fragment or View hosted by it must be `@AndroidEntryPoint`?
   - Hilt Android entry-point semantics for the specific class type (Fragment, Activity, Service, etc.).
3. If none of the above require Hilt Android ownership AND the class has `@Inject constructor()`: it is Category A — no `@AndroidEntryPoint` required. Remove only the `onAttach`/`inject()` call.
4. If none of the above require Hilt Android ownership AND no `@Inject constructor()`: class is fully manual — no Hilt involvement needed.
5. If members exist in a superclass not owned by this feature: determine whether those are already handled by legacy injection or another Hilt entry point before concluding no `@AndroidEntryPoint` is needed.

`@AndroidEntryPoint` is required when Hilt Android ownership is needed (members injection, ViewModel owner requirements, or Android hierarchy requirements). It is NOT required merely because a class was listed in a legacy `inject()` method.

### Constructor injection vs members injection distinction

These are two independent injection mechanisms. A class may use one, both, or neither.

**Constructor injection** (`@Inject constructor(...)`):
- Hilt constructs the object itself, supplying constructor arguments.
- The object's type can appear as a dependency in other Hilt-constructible graphs.
- Scope annotations on the class (`@Singleton`, `@FeatureScope`) control how often Hilt constructs it.
- Custom scope annotations that Hilt does not recognize cause `IncompatiblyScopedBindings` during full Hilt component aggregation.

**Members injection** (`@AndroidEntryPoint` + `@Inject lateinit var`):
- Hilt injects into an already-constructed Android framework object (Fragment, Activity, etc.) by filling its `@Inject`-annotated fields.
- The injection happens at the Android lifecycle boundary (e.g. `onAttach`, `onCreate`).

**The two are independent:**
- `@Inject constructor` alone does NOT imply `@AndroidEntryPoint`. A class with `@Inject constructor()` and no `@Inject lateinit var` fields needs no `@AndroidEntryPoint` purely because of its constructor.
- Being an Android Fragment/Activity/etc. alone does NOT imply constructor injection.
- A Fragment can have both `@Inject constructor()` (used by Hilt to construct helper objects) and `@AndroidEntryPoint` with `@Inject lateinit var` fields — these are orthogonal.

**Determining whether `@AndroidEntryPoint` is required:**
- Inspect the full set of Hilt Android ownership requirements for the class: members injection across the inheritance chain, ViewModel owner requirements, Android component hierarchy requirements, and Hilt-supported entry-point semantics.
- `@Inject lateinit var` fields are one indicator but not the only one. A class with no injectable fields may still require `@AndroidEntryPoint` if it must be a Hilt-owned ViewModel store owner for child fragments.
- A class listed in a legacy `inject()` list with no Hilt Android ownership requirements does not require `@AndroidEntryPoint`.

### Shared-base double-injection migration pattern

When a shared base class unconditionally performs legacy injection but only some subclasses are migrating, use an opt-in hook to guard the legacy path:

```kotlin
// In shared base class — default preserves existing behavior for all non-migrated subclasses
protected open fun isHiltInjected(): Boolean = false

init {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.CREATED) {
            if (!isHiltInjected()) {
                LegacyInjector.inject(activity?.applicationContext, this@BaseFragment)
            }
        }
    }
}
```

```kotlin
// In migrated subclass — opts into Hilt ownership
@AndroidEntryPoint
class MigratedFragment : SharedBaseFragment() {
    override fun isHiltInjected(): Boolean = true
}
```

Rules for this pattern:

- Preserve legacy behavior by default (`return false`)
- Expose the smallest possible ownership hook (single boolean method)
- Migrated subclass explicitly opts in by overriding to `true`
- Guard legacy injector using the hook; do not disable it globally
- Do not hard-code migrated subclass types in the base class
- Do not maintain a list of migrated subclasses in the base class
- This is temporary hybrid infrastructure — document its removal condition

**Nature of coexistence:** The guarded pattern is deterministic, not a race condition. If `isHiltInjected()` returns `true`, `LegacyInjector.inject()` is not called. If Hilt injected the fields before the coroutine fires, there is no overwrite. Confirm by checking injection timing relative to `Lifecycle.State.CREATED`.

**Removal condition:** All subclasses of the shared base class migrated to Hilt; legacy injector no longer needed; `isHiltInjected()` hook and the guard removed.

**The hook never injects.** Overriding `isHiltInjected()` to `true` only skips the legacy injector; Hilt injects because of `@AndroidEntryPoint` on the class (or its generated `Hilt_X` superclass). A subclass that overrides the hook without the annotation gets no injection at all, and its fields stay uninitialized at runtime. The override and `@AndroidEntryPoint` always go together.

---

# Phase 6b — Hilt processing requirement per module

Do NOT apply the Hilt Gradle plugin to a module merely because it contains legacy Dagger code.

For every module affected by the Android entry-point hierarchy:

1. Determine whether Hilt processing is actually required in that module.
2. Inspect the annotations and classes in that module and the bytecode transformation requirements of the current Hilt version.
3. If uncertain, compile the smallest affected variant and use the actual Hilt diagnostic as evidence — compiler output is authoritative over assumptions.
4. Apply the Hilt plugin only to modules proven to require it.
5. Record every newly Hilt-enabled module in the MIGRATION BLAST RADIUS table.

After enabling Hilt processing in a module, inspect all legacy Dagger `@Module` classes in that module.

If Hilt's modules-have-`@InstallIn` validation now rejects an intentionally legacy-owned module, handle it according to Phase 8d.

Never enable Hilt processing in a module solely in order to suppress an `@InstallIn` check.

Do not encode a specific pilot's plugin topology (e.g. "always apply Hilt to the shared views and security modules", as one pilot needed) as a universal rule. Trace the inheritance chain for each new feature and apply the plugin only where the trace proves it is required.

---

# Phase 7 — Determine bridge direction

During incremental feature migration the normal direction is:

```text
LEGACY DAGGER → HILT
```

Example:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LegacySharedBridgeModule {

    @Provides
    fun provideLegacyService(
        @ApplicationContext context: Context
    ): LegacyService {
        val provider =
            context.applicationContext as LegacyComponentProvider

        return provider
            .provideLegacyComponent()
            .exposeLegacyService()
    }
}
```

Do not automatically scope bridge providers.

Preserve underlying lifetime semantics.

---

# Important: Hilt EntryPoint direction

Do NOT use:

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
```

to try to access a binding that exists only in an independently-created legacy Dagger component.

A Hilt EntryPoint exposes bindings that exist inside the Hilt component.

Use Hilt EntryPoints when the ownership direction is reversed:

```text
remaining manual/legacy code
          │
          ▼
needs binding already owned by Hilt
          │
          ▼
Hilt EntryPoint
```

Determine direction before choosing interoperability mechanism.

---

# Phase 8 — Design minimum bridge

Create only bridges required for the target feature.

Before adding each bridge ask:

1. Does Hilt already bind this (with the correct qualifier)?
2. Is it `@Inject` constructible?
3. Can Hilt construct the higher-level consumer if only a lower-level dependency is bridged?
4. Does an existing migration bridge already expose it?
5. Can the legacy binding itself now safely migrate to Hilt?
6. Would adding this bridge widen lifetime?
7. Would it create dependency cycles?

Prefer fewer, lower-level bridges.

### Bridge placement rule

Place bridges according to Gradle dependency direction and ownership:

```text
feature-specific binding needed only by the target feature
    → target feature's own Hilt module (e.g. FeatureHiltModule)

app-wide binding needed by the host Activity or app-level ViewModels
    → app module bridge (e.g. AppHiltBridgeModule)

binding needed by multiple migrated features
    → propose promotion to shared migration infrastructure
      (e.g. a dedicated di-migration module or core/di)
      Only after confirming there is no Gradle dependency cycle.
```

Never create a module dependency cycle to centralize a bridge.

Never duplicate the same bridge in multiple feature modules.

### Bridge ownership promotion rule

Bridges must live according to actual consumer ownership and Gradle dependency direction.

Feature-local placement is correct when:
- only the feature's own Hilt consumers use the binding

App-level or shared placement is correct when:
- the host Activity requires the binding
- multiple migrated features require the binding
- dependency direction prevents the feature module from depending on the owner

Do not promote a binding merely because its type sounds shared.

When a later feature needs an existing feature-local bridge, re-evaluate canonical ownership at that time. Do not duplicate the bridge into a second feature module. Promote the binding to the correct shared location or confirm the feature module can depend on the current owner.

### Bridge lifetime record

For every bridge, record:

| Field | Value |
|---|---|
| Binding key (type + qualifier) | |
| Legacy source | |
| Legacy component construction behavior | (rebuilt per inject? cached? singleton?) |
| Legacy effective lifetime | |
| Hilt provider scope | (unscoped / @Singleton / etc.) |
| Hilt effective lifetime | |
| Object identity implications | (same instance as legacy? new instance?) |
| Consumers | |
| Removal condition | |

Do not infer legacy lifetime from a custom `@Scope` annotation alone. Check whether the component is cached or rebuilt on every injection.

---

# Phase 8b — Host Activity blast radius

This phase is MANDATORY before annotating any `@AndroidEntryPoint` fragment.

Hilt's runtime requirement is:

> Every `@AndroidEntryPoint` Fragment must be attached to an `@AndroidEntryPoint` Activity. There is no runtime bypass.

`@OptionalInject` does not bypass Hilt's compile-time binding validation. It is a runtime mechanism only (`wasInjectedByHilt()`). Do not use it to defer resolving missing bindings.

Before annotating the first fragment in a feature with `@AndroidEntryPoint`:

1. **Identify all host Activities** that can display this feature's fragments (via navigation, deep links, bottom sheets, or direct transactions).
2. For each host Activity not yet annotated `@AndroidEntryPoint`:
   a. Determine whether making it `@AndroidEntryPoint` is acceptable given current migration state.
   b. If yes, enumerate ALL `@Inject` fields on that Activity.
   c. Trace each field's full transitive dependency graph.
   d. Classify every dependency in that graph (Categories A–F).
   e. Enumerate every additional bridge required.
   f. Identify every legacy Dagger inject path for that Activity (XComponent.inject(activity)) and mark them for removal once Hilt takes over.
3. If any host Activity's injection graph cannot be safely resolved in Hilt yet, treat making that Activity `@AndroidEntryPoint` as a BLOCKER and report it.
4. Include all of this in the MIGRATION BLAST RADIUS table.

---

# Phase 8c — Legacy Dagger injection cleanup

Once Hilt is verified as the sole injector for a concrete Android entry point:

1. Remove that entry point's manual legacy inject call (`initInjection()` or equivalent).
2. Search repository-wide for the corresponding legacy component `inject()` method and all its callers.
3. Remove the component `inject()` method only when no remaining legacy path calls it.
4. Remove or simplify the Injector object only after its remaining consumers have been audited.
5. Compile both the Hilt graph and every affected legacy Dagger component after removal.

Never leave Hilt and legacy Dagger independently injecting the same concrete Android entry point unless coexistence is explicitly required and field ownership is proven disjoint.

Failure to remove the legacy component inject method causes the legacy Dagger component's validation graph to reference bindings that have moved to Hilt, producing `MissingBinding` errors in the legacy component.

Do not delete an entire shared legacy component merely because one consumer migrated. Audit all remaining consumers first.

---

# Phase 8d — `disableModulesHaveInstallInCheck` escape hatch

`dagger.hilt.disableModulesHaveInstallInCheck` is a hybrid-migration escape hatch.

Use it only when:

- The Hilt plugin is applied to a module that contains a legacy `@Module` class without `@InstallIn`.
- That `@Module` is intentionally legacy-Dagger-owned and must not be given `@InstallIn` yet.
- Adding `@InstallIn` to it would be incorrect because it is still used as a dependency of a legacy Dagger component.

Before applying this flag, confirm that the affected `@Module`:

- is still actively referenced by a legacy `@Component` `modules = [...]`
- is NOT a candidate for immediate Hilt migration

Configure the flag ONLY in the specific `build.gradle.kts` where the conflict occurs. Do not apply it globally.

Record the affected module and removal condition in the migration ledger:

```text
Module: common/security
Flag: dagger.hilt.disableModulesHaveInstallInCheck = true
Reason: SecurityModule is still used by SecurityComponent (legacy Dagger). Cannot add @InstallIn.
Removal condition: SecurityModule migrated to Hilt or SecurityComponent removed.
```

**Escape-hatch removal conditions:**

`disableModulesHaveInstallInCheck = true` may be removed only when no `@Module`-annotated class on the relevant Hilt-processed compilation classpath still requires that suppression.

A `@Module` no longer requires suppression when one of the following is true:

1. **The source file is deleted** from the compilation classpath (the common case when the legacy module/component/injector are all obsolete).
2. **The module has been legitimately migrated to Hilt** with the correct `@InstallIn` annotation — do NOT add `@InstallIn` merely to eliminate the flag; only add it if the module genuinely belongs in a Hilt component.
3. **The module no longer belongs to the Hilt-processed compilation boundary** — if repository architecture genuinely supports separating it into a non-Hilt-processed module. Confirm this through actual compilation, not assumption.

**Critical:** Hilt's annotation processor scans ALL `@Module`-annotated classes on the compile classpath regardless of whether they are reachable through any live Hilt component. A legacy `@Module` that still exists as a source file on the Hilt-processed classpath will be rejected the moment the flag is removed, even if no Hilt component imports it.

**For the common feature-cleanup case (atomic gate):**

When the legacy `@Module`, `@Component`, `Injector`, and `@Scope` are all obsolete, remove them and the flag in one compilation gate (no intermediate compile step between these edits):

1. Delete the protected legacy `@Module` source file (e.g. `FeatureModule.kt`).
2. Delete the legacy `@Component` source file that referenced it (e.g. `FeatureComponent.kt`).
3. Delete the legacy `Injector` object (e.g. `FeatureInjector.kt`).
4. Delete the legacy `@Scope` annotation (e.g. `FeatureScope.kt`).
5. Remove the `disableModulesHaveInstallInCheck` argument from `build.gradle.kts`.

Attempting to remove the flag before step 1 will result in a build failure:
```text
[Hilt] @Module XxxModule is missing @InstallIn
```

This is not a fixable warning — it requires reverting the flag removal and completing the source deletion first.

---

# Phase 8e — Dead binding removal

Before deleting an apparently unused provider:

1. Repository-wide source search for all references to the binding type and its consumers.
2. Generated Hilt graph search — inspect the generated component file for any factory or injection of the binding.
3. Constructor and member injection search — grep for `@Inject` fields of that type.
4. Compile after removal.

No generated consumer + no source consumer + successful compile is strong evidence of safe deletion.

Do not retain dead migration bindings "just in case." Dead bindings silently mislead future audits.

---

# Phase 9 — Bootstrap Hilt if required

Only in INITIAL_BOOTSTRAP mode.

Inspect actual toolchain first.

Select Hilt version compatible with:

- current Dagger
- Kotlin
- AGP
- Gradle
- JVM
- kapt/KSP

Avoid unnecessary dependency upgrades.

When possible during incremental coexistence, keep Dagger/Hilt versions aligned.

Do not introduce KSP solely as part of Dagger → Hilt migration unless explicitly approved.

For kapt-based Hilt builds configure appropriate Hilt/kapt settings such as `correctErrorTypes` when required by the repository/toolchain.

Apply Hilt only where currently necessary.

Do not automatically modify every feature's convention plugin during the pilot.

Bootstrap:

```text
Application → @HiltAndroidApp
host Activity → @AndroidEntryPoint when required (see Phase 8b)
```

Keep existing legacy Dagger root graph operational.

Compile before continuing.

---

# Phase 10 — Feature migration

After bootstrap/bridge approval:

## Modules

Convert feature Dagger modules to Hilt modules.

Choose correct `@InstallIn` component based on actual lifetime.

Do not default everything to `SingletonComponent`.

---

## ViewModels

Where appropriate:

```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    ...
) : ViewModel()
```

Use the appropriate AndroidX ViewModel delegate based on the required `ViewModelStoreOwner`.

Do not blindly prescribe a helper/delegate name.

Use the API supported by the repository's AndroidX/Hilt versions and required ownership.

Before replacing an injected ViewModel field with a Hilt delegate, apply the ViewModel field visibility rule from Phase 5.

If the migration changes lifecycle semantics (e.g. object now survives rotation), state this explicitly in the migration plan and require approval before implementation.

---

## Fragments / Activities / Dialogs

Add `@AndroidEntryPoint` to concrete Android entry points that require Hilt injection.

Before annotating any fragment, complete Phase 8b (Host Activity blast radius).

Remove only the target feature's obsolete manual injection calls.

When Hilt takes over injection for an entry point, apply Phase 8c (cleanup rule).

Keep unrelated legacy injection paths intact.

---

# Phase 10b — Secondary ViewModel elimination (optional, requires explicit approval)

## When this phase applies

This phase is an **architecture refactor, not a DI change**. Under Absolute rule 4 it runs only when the user asks for it or approves it in the migration plan. Never start it implicitly.

It is the right tool when a migrated feature's entry points (Fragments, Activities, Dialogs) inject ViewModels that **belong to other modules** (shared `common`/`core` modules or other features), because:

- Those classes usually cannot become `@HiltViewModel` without applying Hilt to modules that legacy features still share (blast radius, escape hatches, shared consumers).
- Left as injected fields they are plain Dagger-built objects: not rotation-safe, and every entry point is coupled to its peers' ViewModels.

## Vocabulary

| Term | Meaning |
|---|---|
| **Primary ViewModel** | The ViewModel that owns one entry point's feature logic and state. It lives in the feature module and is `@HiltViewModel`. |
| **Secondary ViewModel** | Any ViewModel defined outside the feature module (or outside that entry point's responsibility) that the entry point injects, observes or calls. |

## Target shape

```text
BEFORE                                   AFTER

EntryPoint ──► PrimaryViewModel          EntryPoint ──► PrimaryViewModel ──► UseCase A
     │                                                                   ──► UseCase B
     └──────► SecondaryViewModel ──► UseCase A                           ──► UseCase C (new, extracted)
                                    └ logic (not a use case)
```

Rules of the target shape:

1. An entry point talks **only** to its primary ViewModel. It never injects, observes or calls a secondary ViewModel, directly or through the primary ViewModel (`primary.secondaryViewModel.x` is a violation too).
2. The primary ViewModel owns the state (LiveData/StateFlow), the jobs and the error handling that the secondary ViewModel owned for this feature.
3. Everything the secondary ViewModel did goes through **use cases** injected into the primary ViewModel.
4. The secondary ViewModel stays untouched for its other consumers (see "Shared module rule").

## The extraction rule (most important)

For every member of a secondary ViewModel that the feature uses, classify it:

| Class | What it is | Action |
|---|---|---|
| **A. Delegation** | Calls an existing use case and publishes the result | Inject that existing use case into the primary ViewModel; re-create the state, job and error handling there |
| **B. Pure logic** | Computes something without a use case (filtering an enum, parsing, finding in a list, regex, formatting) | **Extract into a new use case.** Never copy the body into the primary ViewModel |
| **C. Logic over loaded state** | Reads the ViewModel's own cached result (e.g. "find item by code in the loaded list") | Extract the pure part into a new use case that takes the data as a parameter; keep the state in the primary ViewModel and pass it in through a thin wrapper |
| **D. Orchestration** | Branches, calls several use cases, touches other collaborators (session, analytics, flags, other ViewModels) | Extract the whole orchestration into one new use case; the primary ViewModel keeps only its state and a thin call |
| **E. Not used by this feature** | No call site in the feature | **Leave it behind.** Do not move members "just in case" |

If a function does not simply delegate to an existing use case, copying its body into the primary ViewModel is **forbidden**: that moves the problem instead of solving it.

## Procedure

### Step 1 — Inventory

For the feature module, list every ViewModel type that is defined **outside** it and appears in its entry points or ViewModels:

- injected fields (`@Inject lateinit var`), constructor parameters, `by viewModels()`/`activityViewModels()` delegates;
- ViewModels injected **into other ViewModels** (the "ViewModel-in-ViewModel" case, e.g. a primary ViewModel holding a public secondary ViewModel that fragments reach through);
- classes that are not entry points but still hold a ViewModel (use cases, adapters).

For each, record: owner module, consumers inside the feature, number of consumers **outside** the feature, whether it is a real `ViewModel` subclass, and whether it holds app-wide state.

Dead-code checks:

- Judge usage by the **declared class name**, never the file name (a file can declare a differently named class).
- A ViewModel with zero references is dead: do not convert it.

### Step 2 — Classify members (table above)

Read each secondary ViewModel completely. List only the members the feature actually calls or observes. Use the compiler, not only grep, to confirm usage: calls through an implicit receiver (`primary.apply { secondaryMember … }`) are invisible to a `.member` search.

### Step 3 — Pick the primary ViewModel for each entry point

| Situation | Primary ViewModel |
|---|---|
| The entry point already has a ViewModel | Use it. |
| A **dialog or screen has no ViewModel of its own** (the secondary one was its only ViewModel) | Create a dedicated `@HiltViewModel` for it, following any precedent in the feature (e.g. an existing `<Dialog>ViewModel`). Report it in the plan: it adds a class. |
| An **abstract base entry point** needs the logic and subclasses have different ViewModels | Create an abstract base ViewModel that owns the use case; make each subclass ViewModel extend it (constructor parameter passed up); give the base entry point `abstract val viewModel: BaseViewModel` and have subclasses `override val viewModel by viewModels()`. |
| Several entry points need the same logic | Each gets the use cases in its own primary ViewModel. Accept the small duplication of state/job blocks; record it as a follow-up (shared base or delegate). |

### Step 4 — Design the use cases

- Place new use cases in the **module that owns the secondary ViewModel**, in the same use-case package as its siblings, so other consumers can reuse them.
- Use constructor injection (`class XUseCase @Inject constructor(private val dep: Dep)`), unless the surrounding code of that module is uniformly field-injected and the user asks to match it.
- Name by behavior (`FindCityUseCase`, `GetMatchingCityUseCase`, `ParseNationalIdUseCase`, `CountdownTimerUseCase`), not by the old ViewModel.
- **Collaborators that are themselves ViewModel-like** (URL/flavor managers, session objects) are wrapped by a use case too, so the primary ViewModel does not depend on them directly (e.g. `IsRetailApiActiveUseCase`). Exception: an app-wide singleton session object that the whole app shares (bridged from the legacy graph) is **not** put into a ViewModel store and is not wrapped without explicit approval; list it as an open decision.
- Keep one use case per cohesive behavior. For orchestration (class D) return a small sealed result tagged by path, so the primary ViewModel can route each path to the state it already had.
- Preserve per-path error handling: a use case replaces the ViewModel's job-failure helper (profile) by logging, emitting the loader-off state, then an error state **unless the exception is a `CancellationException`**, which it rethrows.

### Step 5 — Shared module rule

The secondary ViewModel keeps serving its other consumers. Therefore:

- Default: **leave it unchanged** and accept a small duplication of the extracted logic (a few lines).
- If the extracted logic is large (rule of thumb: more than ~30 lines, or anything with regexes/parsers), make the shared ViewModel **delegate to the new use case** instead of keeping two copies. This is the only reason to edit the shared class. Keep its public API identical and its constructor change DI-only; verify no code constructs it manually; build every dependent module.
- Record the duplication or the delegation in the ledger as hybrid debt with a removal condition.

### Step 6 — Apply the edit safely

- **Copy logic mechanically.** Move large bodies by line range with `sed`/scripts, never by retyping. Regexes lose backslashes when retyped (`"\\s+"` becoming `"\s+"` fails to compile or silently changes behavior). Apply only the minimal edits: rename the function, add parameters that replace former fields.
- Edit files with a method that **preserves their line endings** (check for CRLF first) and produces small diffs. A whole-file diff means line endings were damaged.
- Do not leave temp files (`*.new`) in source trees.
- Do not edit a file you did not recently touch without checking its modification time: the user or the IDE may be editing it. An IDE with a stale open buffer can silently write old content back; if a file reverts, say so and ask the user to reload from disk.
- Visibility: keep a constructor property `private val` unless the compiler shows an outside access (typically through an implicit receiver). Then make only that property public, as it was before.

### Step 7 — Behavior-preservation checklist

For every moved member verify, by reading both versions:

- same job cancellation, same dispatcher, same loader/error emissions per path;
- same default parameters; **no added guards** (an original `find` without a null check must stay without one);
- dead or constant branches (a flag hardcoded to `true`) are preserved and **reported**, not silently removed;
- unused parameters of the old API that no caller sets may be dropped, and the drop is reported;
- fields of the moved state that could now outlive the entry point (Drawable, View, Context, NavController, binding): none may be kept in the primary ViewModel. Move them to a local variable or a parameter.
- The state now lives in a `@HiltViewModel`, so it **survives rotation**: observers receive the last value again. State that must reset must be reset explicitly; document it in the plan.

## Gate shape

- Do **one secondary ViewModel per gate**.
- Start with a **pilot** (the simplest secondary ViewModel with a delegation and a pure-logic member), apply it, verify it, and **show the result before continuing**.
- Each gate's verification ladder: compile the module that owns the new use cases, the feature module (annotation processing then Kotlin compile), and the application module. Attribute every compile error to a file; errors in files you did not change are reported separately.
- When a shared module changed, build **all** its dependents, not only the feature.
- After each gate update the ledger: new use cases, new primary ViewModels, removed secondary ViewModel usages, remaining consumers, hybrid debt (duplication or delegation).

## Anti-patterns (do not do)

- Copying a secondary ViewModel's logic into the primary ViewModel.
- Keeping the secondary ViewModel and just injecting it into the primary one (`primary.secondary` exposed to the entry point).
- Moving members the feature does not use.
- Wrapping a bridged app-wide singleton in a per-screen ViewModel store.
- Converting the shared ViewModel itself to `@HiltViewModel` as a shortcut when its module is shared with legacy features.
- Retyping regexes or long parsers.
- Declaring a ViewModel dead from its file name.

---

# Phase 11 — Compile gate

Do not migrate the entire feature in one uncontrolled edit.

**kapt success is NOT source compilation success:**

Annotation processing (kapt) and Kotlin source compilation are different build stages — discover their actual task names in the repository before running them.

- The **annotation processor task** (kapt) runs Hilt and Dagger processors. It validates graph declarations.
- The **Kotlin source compile task** compiles Kotlin source. It validates type resolution, import resolution, delegate calls.
- The **full target variant build/assemble task** performs Hilt's cross-module component aggregation. It may fail even when the annotation processor and source compile tasks pass individually.

A gate is NOT complete based on annotation processing alone. The verification ladder (Phase 13) must always include the source compile step and, for any gate that modifies the Hilt graph, the full app build/assemble task that performs complete Hilt aggregation.

**Errors that appear only during full Hilt aggregation (not at annotation processing):**
- `Dagger/IncompatiblyScopedBindings` — custom scope annotations on constructor-injectable classes
- Cross-module aggregation failures
- Component hierarchy mismatches

**Atomic gates may contain non-compilable intermediate edit states:**

An atomic gate is a set of source edits that must all be applied before any compilation attempt. The intermediate state after applying some but not all edits within the same gate is INTENTIONALLY non-compilable.

Do NOT compile an intermediate state within an atomic gate.

Example: If a gate requires:
1. Add `@HiltViewModel` to N ViewModels
2. Change N fragments to `by viewModels()` delegate
3. Remove N legacy `inject()` calls from FeatureComponent
4. Remove N `onAttach`/`initInjection` overrides from fragments

Compiling after step 1 alone may fail because the ViewModel is in the Hilt graph while the fragment still calls `FeatureInjector.inject()`. If compiler evidence confirms rejection, steps 1–4 form a single atomic gate.

The correct boundary for "compile" is between gates, not between individual file edits within a gate.

Recommended gates:

```text
Gate A
Hilt bootstrap + host Activity @AndroidEntryPoint + host Activity bridges
        ↓ compile

Gate B
feature-specific bridges (bridge ownership confirmed per Phase 8 rules)
        ↓ compile

Gate C
feature Hilt modules + dependency graph
        ↓ compile

Gate D+E+F (potentially atomic — see ViewModel migration atomicity invariant)
@HiltViewModel on ViewModels (with explicit lifecycle change documentation)
+ @AndroidEntryPoint on consuming entry points
+ removal of legacy inject() declarations from FeatureComponent/FeatureInjector
+ removal of legacy onAttach/initInjection calls from entry points
        ↓ compile
[Apply as one atomic gate only if compiler evidence proves the intermediate state
 is rejected. See the ViewModel migration atomicity invariant in Phase 5.]

Gate F2 (optional, approval required — Phase 10b)
secondary ViewModel elimination: ONE secondary ViewModel per gate
(pilot first, show the result, then continue)
        ↓ compile owner module + feature module + app module (+ all dependents if a shared
          module changed)

Gate G
remove remaining obsolete legacy DI infrastructure (FeatureComponent, FeatureInjector,
FeatureModule, FeatureScope) + remove escape hatch if applicable
(see Phase 8d — escape-hatch removal conditions — these deletions must be atomic)
        ↓ compile + tests

Gate H (post-completion)
provenance audit — compare working tree against HEAD (see Phase 12b)
        ↓ FEATURE_HILT_COMPLETE declaration
```

If a gate fails:

STOP.

Diagnose the root cause using the error classification below.

Do not respond to every `MissingBinding` by blindly adding a bridge.

Trace ownership first.

---

## Compiler error classification

Classify every compilation failure before changing code.

### MissingBinding

Trace the missing type to its actual owner.

Determine the correct resolution — in priority order:

1. Hilt can constructor-inject it — let it.
2. An existing Hilt binding already covers it — reuse it.
3. An existing bridge already exposes it — reuse it.
4. The feature module should provide it via its own Hilt module.
5. A temporary legacy bridge is genuinely required.
6. The host Activity expansion has enlarged the migration graph — see Phase 8b.

Do NOT automatically create a bridge for every `MissingBinding`.

### DuplicateBinding

Determine whether both Hilt and a legacy migration module now expose the same type **and qualifier**.

Prefer removing obsolete migration infrastructure over adding qualifiers to silence the error.

Before removing a binding, apply Phase 8e (dead binding removal checks).

### Missing `@InstallIn`

Determine whether the `@Module` should actually migrate to Hilt with `@InstallIn`, or whether it remains intentionally legacy-owned.

If legacy-owned, apply the Phase 8d escape hatch.

Do NOT add `@InstallIn` blindly.

### `@AndroidEntryPoint` / generated-superclass / plugin errors

Trace:

```text
@AndroidEntryPoint class
    ↓
superclass
    ↓
owning Gradle module
    ↓
Hilt plugin configuration of that module
```

Apply the plugin only to the module identified above.

Do NOT enable Hilt processing repository-wide.

### Legacy Dagger MissingBinding (in a non-Hilt component)

Determine whether the migration removed a binding while a legacy Dagger component still has an `inject()` method referencing the migrated entry point.

Audit the legacy injection path — see Phase 8c — before restoring the binding.

### Scope mismatch

Compare the actual legacy component lifetime against the Hilt component lifetime.

Do NOT fix scope errors by promoting dependencies to `@Singleton`.

---

# Phase 12 — Remove target feature's legacy DI

Only after the migrated feature compiles and works under Hilt.

Search repository-wide before deleting:

```text
FeatureComponent
FeatureInjector
FeatureScope
FeatureModule
DaggerFeatureComponent
```

Check:

- Kotlin/Java references
- XML/navigation
- reflection
- generated integration patterns
- tests
- debug/flavor sources

Delete only proven-unused legacy infrastructure.

---

# Phase 12b — Mandatory post-migration provenance audit

Before declaring `FEATURE_HILT_COMPLETE`, compare the working tree against the migration baseline (HEAD).

**This is mandatory. Do not skip it.**

Run:

```bash
git status --short
git diff --stat HEAD
```

For every migration-related changed file, classify:

| Classification | Meaning |
|---|---|
| `REQUIRED_FINAL_CHANGE` | Permanent migration change; not reversible |
| `TEMPORARY_HYBRID_CHANGE` | Needed during transition; has a documented removal condition |
| `OBSOLETE_CHANGE` | No longer needed; should be reverted or cleaned up |
| `UNRELATED_CHANGE` | Pre-existing or unrelated to migration; do not include in migration scope |
| `NEEDS_REVIEW` | Uncertain — requires explicit user decision |

Account for:

- deleted legacy DI files (confirm zero remaining source references)
- new Hilt module files
- modified fragment/activity/ViewModel files
- modified build files
- modified shared base classes

This prevents deleted legacy infrastructure from disappearing from the audit simply because it no longer exists in the current filesystem. Legacy artifacts that existed at HEAD and were deleted by migration are `REQUIRED_FINAL_CHANGE` and must be recorded explicitly.

---

# Phase 13 — Verification

Run actual repository tasks.

Never invent Gradle task names.

Discover variants/tasks first.

Use the verification ladder in order — smallest useful first:

1. **Affected module annotation processing** — catches Hilt/Dagger graph errors in the target module; does NOT prove source compilation
2. **Affected module source compile** — proves Kotlin source compiles (imports, delegate calls, type resolution); distinct from annotation processing
3. **Dependent Hilt aggregation / app annotation processing** — catches cross-module graph errors
4. **App target variant build/assemble** — performs full Hilt component aggregation across all modules; catches `IncompatiblyScopedBindings` and cross-module failures that annotation processing alone will not surface; this is the authoritative full-graph verification task
5. **Generated graph inspection** — use when binding ownership is ambiguous; inspect the generated Hilt component source (e.g. `DaggerApp_HiltComponents_SingletonC.java` or equivalent discovered in the build output)
6. **Repository search for removed symbols** — confirms zero remaining legacy consumers
7. **Runtime smoke tests** — confirms behavior and lifecycle correctness

**For any gate that modifies the Hilt graph (adds `@HiltViewModel`, `@AndroidEntryPoint`, `@InstallIn` modules, or removes scope annotations), step 4 is mandatory — not optional.**

Discover actual task names in the repository; do not invent or hardcode them.

A successful annotation-processing run does not prove source compilation. A successful source compile does not prove full Hilt aggregation. Both are required for each meaningful gate.

A successful compile does not by itself prove runtime ownership or lifetime equivalence. ViewModel lifecycle changes, shared binding identity, and injection timing require runtime verification.

Report exact commands and PASS/FAIL.

---

# Runtime verification

Create feature-specific smoke paths based on actual behavior.

Always include when relevant:

- cold start
- navigation into feature
- happy path
- error path
- rotation (especially critical when Dagger-created non-ViewModels are converted to real ViewModels)
- process recreation
- back-stack behavior
- deep links
- session expiry
- dialogs
- shared ViewModels
- parent-fragment interactions
- analytics
- signing/security flows
- flavor-specific behavior

Do not claim runtime verification occurred unless it actually occurred.

---

# Phase 14 — Regression protection

The application is in hybrid Hilt + Dagger state.

After each migration verify that non-migrated features still compile.

Do not modify their Dagger graphs merely to satisfy the migrated feature unless the change is additive and explicitly justified.

Shared component changes require additional caution.

---

# Migration ledger

Maintain a repository migration ledger.

Preferred location (the profile says whether this repository already has one elsewhere):

```text
docs/di-migration/status.md
```

If it does not exist, propose creating it.

Do not create it until approved.

Recommended structure:

```markdown
# DI Migration Status

## Global

Hilt bootstrap: COMPLETE
Legacy Dagger remaining: YES
Final cleanup: NOT READY

## Modules

| Module | Status | Legacy component removed | Bridges used | Notes |
|---|---|---|---|---|
| feature_a | HILT | yes | Base, Common | |
| feature_b | DAGGER | no | - | |
| feature_c | DAGGER | no | - | |

## Shared infrastructure

| Component | Status | Remaining legacy consumers |
|---|---|---|
| BaseComponent | DAGGER | ... |
| CommonComponent | DAGGER | ... |
| SecurityComponent | DAGGER | ... |

## Temporary bridges

| Binding key | Legacy owner | Hilt bridge location | Legacy effective lifetime | Hilt scope | Consumers | Removal condition |
|---|---|---|---|---|---|---|
| Context (unqualified) | BaseComponent | AppHiltBridgeModule | per-component | unscoped | SigningBaseFragment subclasses | All consumers audited and migrated to @ApplicationContext/@ActivityContext |
| Retrofit | BaseComponent | AppHiltBridgeModule | singleton | unscoped | multiple features | BaseComponent migrated to Hilt |

## Hybrid debt

| Mechanism | Location | Reason | Removal condition |
|---|---|---|---|
| isHiltInjected() hook | SigningBaseFragment | Shared base performs legacy injection; migrated subclasses must opt out | All SigningBaseFragment subclasses migrated to Hilt |
| disableModulesHaveInstallInCheck | common/security | SecurityModule is legacy-owned | SecurityModule migrated to Hilt or SecurityComponent removed |
```

The ledger prevents duplicate bridges and tracks when temporary migration infrastructure can be removed.

### Migration debt granularity

Classify migration debt at binding/mechanism granularity, not automatically at
module-file granularity.

A Hilt module may contain both:
- permanent native Hilt bindings
- temporary migration bridges

Do not classify the entire Hilt module as temporary merely because some
providers inside it are temporary.

Track each temporary provider independently in the migration ledger with its
own owner, consumers, lifetime semantics, and removal condition.

---

# Temporary bridge lifecycle

Every bridge must have a removal condition.

Example:

```text
Bridge:
CommonService (unqualified)
    CommonComponent → Hilt

Created because:
feature_a migrated before Common.

Legacy effective lifetime:
CommonInjector.buildComponent(context) constructs a fresh CommonComponent per call. @CommonScope therefore scopes CommonService only within that particular component instance.

Effective lifetime: per CommonComponent build/injection unless the returned binding is explicitly cached elsewhere.

Hilt scope:
unscoped (new instance on each provision unless cached at call site).

Object identity note:
Identity depends on the bridge implementation and underlying dependency. Do not assume singleton identity. Verify whether the bridge returns an object from a newly-built CommonComponent or constructs it from another canonical binding.

Reuse:
feature_b may reuse it. Consult ledger before adding a duplicate.

Removal condition:
CommonService/CommonRepository becomes natively Hilt-owned and no legacy ownership is required.
```

Never allow a temporary bridge to become undocumented permanent architecture.

---

# Migration direction rule

Every feature migration should ideally move the repository monotonically toward Hilt.

After a migration:

```text
Hilt-owned bindings >= before
Legacy feature DI < before
```

Avoid introducing new legacy-Dagger dependencies.

If a proposed migration increases legacy coupling, stop and explain why.

---

# Shared bridge promotion

If a bridge initially created for one pilot feature becomes required by additional migrated features:

Do not copy it.

Evaluate moving it from:

```text
features/feature_a/.../LegacyCommonBridgeModule
```

to an appropriate shared migration/DI module.

The location must respect Gradle module dependency direction.

Do not create reverse dependencies or dependency cycles merely to centralize a bridge.

---

# Final shared-component migration

Once enough features have migrated, analyze shared components.

Example strategy:

```text
CommonService
currently:
CommonComponent owns it
        ↓
bridge
        ↓
Hilt consumers

later:
Hilt owns CommonService
        ↓
legacy consumers temporarily access Hilt if required
```

At this point interoperability direction may reverse:

```text
HILT → LEGACY
```

This is where Hilt EntryPoints may become appropriate.

Do not keep Dagger authoritative merely because it was authoritative at the beginning.

Ownership should progressively move toward Hilt.

---

# Final Dagger removal audit

Before declaring migration complete, search repository-wide for at least:

```text
@Component
@Subcomponent
Dagger[A-Z]
@Component.Builder
@Component.Factory
@Subcomponent.Builder
@Subcomponent.Factory
@ContributesAndroidInjector
dagger.android
AndroidInjection
AndroidSupportInjection
DispatchingAndroidInjector
HasAndroidInjector
DaggerApplication
DaggerAppCompatActivity
DaggerFragment
DaggerDialogFragment
ViewModelKey
DaggerViewModelFactory
ComponentProvider
Injector
@Scope
```

Also inspect:

- Gradle version catalog
- convention plugins
- buildSrc
- kapt dependencies
- test source sets
- debug source sets
- flavor source sets

Do not treat every `@Inject`, `@Provides`, `@Binds`, or `@Module` as legacy Dagger usage because Hilt itself uses Dagger annotations.

Classify results.

---

# Feature completion definition

A feature is `FEATURE_HILT_COMPLETE` only when ALL of the following are true:

1. Every concrete Android class requiring Hilt Android ownership (members injection, ViewModel owner requirements, or Android hierarchy requirements) is correctly configured as `@AndroidEntryPoint`; obsolete legacy injection paths for classes not requiring Hilt Android ownership are removed (see Phase 6 — legacy inject target ≠ Hilt entry point)
2. Feature ViewModel ownership is explicit, correct, and documented (including any lifecycle changes)
3. Feature-owned legacy component is deleted — confirmed by provenance audit
4. Feature-owned legacy injector is deleted — confirmed by provenance audit
5. Feature-owned legacy module is deleted or has a justified remaining role
6. Feature-owned obsolete custom scope is deleted — confirmed by provenance audit
7. All legacy `FeatureInjector.inject()` call sites are removed from source
8. No external source consumer still requires the feature legacy graph — confirmed by repository search
9. Affected module kapt passes
10. App Hilt aggregation/kapt passes
11. Full target variant build passes
12. Provenance audit (Phase 12b) is complete and all changes classified
13. *(Only if Phase 10b was approved)* No entry point of the feature injects, observes or calls a ViewModel defined outside the feature, directly or through its primary ViewModel. Documented exceptions (for example an app-wide bridged session object) are listed with the user's decision.

A feature **may** still depend on shared legacy infrastructure through explicit temporary bridges. This is expected and correct.

Distinguish clearly:

```text
FEATURE_HILT_COMPLETE
    The feature itself is fully Hilt-owned.
    It depends only on documented temporary bridges to shared legacy infrastructure.

PROJECT_HILT_COMPLETE
    All features are FEATURE_HILT_COMPLETE.
    All shared legacy components are migrated or removed.
    All temporary bridges are removed.
    All escape hatches are removed.
    All obsolete Dagger infrastructure is removed.
    Migration ledger shows zero remaining legacy consumers.
```

---

# Completion criteria

The application migration is complete only when:

1. all target Android entry points use Hilt where appropriate
2. all application DI ownership has moved to Hilt
3. no feature relies on manual Dagger components
4. no shared legacy component is required
5. no temporary bridge remains
6. no legacy Injector object remains
7. obsolete custom scopes are removed
8. legacy Dagger ViewModel factory/multibinding infrastructure is removed
9. Dagger Android infrastructure is removed if present
10. direct legacy Dagger dependencies/processors are removed where no longer required
11. app builds/tests pass
12. migration ledger reports zero remaining legacy consumers

---

# Required analysis output

Before implementing a target module, return:

## MIGRATION MODE

`INITIAL_BOOTSTRAP`, `FEATURE_MIGRATION`, `SHARED_INFRASTRUCTURE_MIGRATION`, or `FINAL_DAGGER_REMOVAL`.

## TARGET

Target module and feature.

## GIT BASELINE

Working tree state vs HEAD for target feature. Provenance classification for all relevant artifacts.

## CURRENT GRAPH

Current component/injector/module/dependency structure, recovered from HEAD if working tree has deletions.

## COMPONENT LIFETIMES

| Component | Declared scope | Actual construction | Cached? | Actual lifetime |
|---|---|---|---|---|

## DI INVENTORY

| Binding | Consumer | Current owner | Actual lifetime | Proposed Hilt source |
|---|---|---|---|---|

## VIEWMODEL OWNERSHIP

| VM | Current creation | Current owner | Shared? | Survives rotation today? | Proposed Hilt owner | Lifecycle change? |
|---|---|---|---|---|---|---|

## SECONDARY VIEWMODEL ELIMINATION PLAN

Include only when the user asked for Phase 10b. One row per secondary ViewModel:

| Secondary VM | Owner module | Users outside the feature | Entry points that use it | Primary VM per entry point (existing / new / abstract base) | Members used → class (A–E) | Use cases to inject (existing) | Use cases to create (new, with module) | Shared-module action (leave / delegate) | Open decisions |
|---|---|---|---|---|---|---|---|---|---|

Also list: ViewModels that are dead (by declared class name), ViewModels deliberately left (with reason, e.g. app-wide session object), and expected duplication.

## ANDROID ENTRY POINTS

Concrete Android classes requiring migration and relevant inheritance/injection behavior.

## EXISTING HILT INFRASTRUCTURE

Bindings and bridges that can be reused. Consult migration ledger.

## NEW BRIDGES

Only genuinely required new temporary bridges.

For every bridge include:

- binding key (type + qualifier)
- legacy owner
- legacy component construction behavior
- legacy effective lifetime
- Hilt scope
- consumers
- proposed location
- removal condition

## MIGRATION BLAST RADIUS

Every class, module, or Dagger path that must change as a consequence of migrating the target — including changes outside the target feature boundary.

| Trigger | Affected class/module | Why migration expands here | Required action |
|---|---|---|---|

## HYBRID MIGRATION DEBT

Every temporary mechanism introduced by this migration.

| Temporary mechanism | Module | Why required | Scope | Removal condition |
|---|---|---|---|---|

## FILE PLAN

Exact create/edit/delete list.

## RISKS

Behavior/lifetime/build/runtime risks. Include explicit documentation of any lifecycle changes to ViewModels.

## VERIFICATION

Actual repository Gradle tasks and runtime smoke plan. Follow the verification ladder.

## BLOCKERS

Anything preventing safe migration.

## VERDICT

`READY_FOR_APPROVAL` or `BLOCKED`.

Stop here.

Wait for explicit approval before implementation.

---

# Required implementation behavior

After approval:

1. implement one gate at a time
2. compile after each meaningful gate — use the full verification ladder, not kapt alone
3. stop on architectural failures — apply the compiler contradiction rule (Absolute rule 7)
4. trace MissingBindings rather than blindly adding bridges
5. preserve non-migrated Dagger features
6. update migration ledger
7. report exact changes
8. report exact commands/results
9. document any lifecycle changes to ViewModels explicitly
10. complete Phase 12b provenance audit before declaring FEATURE_HILT_COMPLETE
11. report newly discovered reusable migration patterns

Never commit, push, merge, deploy, publish, or release without explicit approval.

---

# Recommended invocation contract

This section defines the expected interaction protocol between the user and this skill. Follow it unless the user explicitly overrides.

## Starting a new feature migration

The user provides: target feature/module name.

The skill responds with: Phase 0 + Phase 1 output, then the full required analysis output (migration plan) ending in `READY_FOR_APPROVAL` or `BLOCKED`. No file modifications during this phase.

The user explicitly approves before implementation begins.

## During implementation

Report at each gate boundary:
- files modified (list with brief description)
- files deleted (with provenance classification)
- compilation result (exact task name and PASS/FAIL)
- any contradictions discovered vs approved plan (STOP immediately if found)

Do not silently proceed past a gate failure. Do not add bridges speculatively during implementation to pre-empt predicted failures.

## Gate failures

At any gate failure:

1. Report the exact error (task name, error class, first relevant stack frame or binding chain).
2. Classify the error using Phase 11's error classification.
3. Propose the smallest safe resolution.
4. Wait for approval before applying the resolution.

Do not resume from a known STOP condition without explicit user acknowledgment.

## Declaring completion

Before declaring `FEATURE_HILT_COMPLETE`:

1. Complete Phase 12b provenance audit.
2. Report the audit table.
3. Report the updated migration ledger.
4. Confirm all Feature completion definition criteria (Phase completion criteria) are met.

Do not claim completion based on compilation alone. State each criterion explicitly.

## Known environment issues

### Editing source files while the user's IDE has them open

**Symptom:** a file you already cleaned contains the old content again after a build (for example a removed helper reappears), or a compile fails on a file you did not touch.

**Cause:** the IDE saved a stale open editor buffer, or the user is editing the same file by hand.

**Rule:** before editing, compare the file's modification time with your last edit. If it changed without you, treat it as a concurrent edit: do not overwrite it, report it, and re-verify after the user reloads from disk. Do not start a build that reads files you are about to edit.

### Mechanical editing pitfalls

- **Line endings.** Check each file for CRLF before editing; preserve it and the presence or absence of the final newline. A whole-file diff after a small edit means the line endings were damaged.
- **Regexes and escapes.** Never retype a regex or a long body. A lost backslash (`"\\s+"` becoming `"\s+"`) is a compile error or a silent behavior change. Move code by line range.
- **Scripted edits fail silently.** After every scripted edit, grep for the expected result and for leftovers; a pattern that does not match changes nothing and can leave a file half-converted (for example an import removed but its call still there). Remove temp files the script created.
- **Implicit receivers.** `x.apply { member }` hides usages from a `.member` search; the Kotlin compiler is the authority on visibility.
- **Stale build outputs.** A compile-classpath jar of a module can be stale after an interrupted build; the symptom is a class that exists in `build/tmp/kotlin-classes` but "could not be resolved" by a dependent. Rebuild the jar task for that module before concluding anything.

### Gradle cache corruption after rebasing onto a branch with new Hilt dependencies

**Symptom:** Build fails at `kaptXxxKotlin` with:
```
zip END header not found
java.lang.NullPointerException: Cannot invoke "com.sun.tools.javac.code.Scope$StarImportScope.isFilled()"
```

**Cause:** Hilt annotation processor jars were added or upgraded on the target branch. The local `~/.gradle/caches` contains corrupt or incomplete entries for the new processors — javac tries to read truncated zips inside the kapt pipeline.

**This is not a conflict resolution error.** No source files need to change.

**Fix (run once per machine; `<assembleVariant>` is the app's variant assemble task from the profile):**
```bash
./gradlew clean
rm -rf ~/.gradle/caches/transforms-* ~/.gradle/caches/build-cache-*
./gradlew <assembleVariant> --refresh-dependencies
```

If the above still fails, nuke the full cache:
```bash
rm -rf ~/.gradle/caches
./gradlew <assembleVariant>
```

These commands delete local caches: say what they do and get the user's go-ahead before running them.

**Who is affected:** Every team member who pulls or rebases onto the branch for the first time after the new Hilt dependencies were introduced. The fix is a one-time local action — once Gradle re-downloads the jars cleanly it will not recur for those dependency versions.

---

### @HiltViewModel field injection silently does nothing

**Symptom:** `UninitializedPropertyAccessException` at runtime on a field declared as `@Inject lateinit var` inside a `@HiltViewModel` class.

**Cause:** Hilt only injects `@HiltViewModel` classes via constructor — field injection inside a ViewModel is silently ignored.

**Fix:** Move all injected dependencies to the constructor:
```kotlin
// Wrong — field is never injected
@HiltViewModel
class MyViewModel @Inject constructor() : BaseViewModel() {
    @Inject lateinit var useCase: MyUseCase  // silently uninitialized
}

// Correct
@HiltViewModel
class MyViewModel @Inject constructor(
    private val useCase: MyUseCase
) : BaseViewModel()
```

---

### A Hilt Fragment's context is not the Activity

**Symptom:** after a Fragment moves to `@AndroidEntryPoint`, something that worked before silently does nothing: a header's default back arrow, a custom view that starts an Activity, a dialog helper.

**Cause:** in an `@AndroidEntryPoint` Fragment, `getContext()` (and the context its views are inflated with) is a Hilt `ContextWrapper`, not the `Activity`. Code that checks `context is Activity` or casts `context as Activity` fails that check without an error.

**Rule:** while auditing a feature's entry points (Phase 6), grep the custom views and helpers its layouts use for `is Activity`, `as Activity`, `as? Activity`, `as AppCompatActivity`. For each hit, either set the explicit callback the view offers in the migrated Fragment (`header.onBackClick = { findNavController().navigateUp() }`), or fix the helper to unwrap the context (`generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()`) in its own change. Add every view found this way to the profile.

### isHiltInjected() override requires @AndroidEntryPoint

**Symptom:** A subclass overrides `isHiltInjected(): Boolean = true` but fields remain null at runtime — injection silently does nothing.

**Cause:** `isHiltInjected()` only opts out of the legacy injector path. It does **not** trigger Hilt injection. Hilt injection happens because of `@AndroidEntryPoint` on the class (or a generated `Hilt_XFragment` superclass). Without the annotation, overriding the flag skips the legacy injector without activating Hilt — the fragment ends up with no injection at all.

**Rule:** Every subclass that overrides `isHiltInjected(): Boolean = true` **must** also be annotated with `@AndroidEntryPoint`. The two always go together.

---

## Session restart behavior

At the start of each new session involving this skill:

1. Re-run Phase 0 (read current source, git baseline, migration ledger).
2. Do not assume prior session state is preserved in memory.
3. Verify the state of a previously declared `FEATURE_HILT_COMPLETE` feature when it is relevant to the current session — specifically when it:
   - participates in the target feature's dependency graph,
   - provides a binding or bridge reused by the target,
   - shares infrastructure being modified by the current migration, or
   - repository evidence (git status, unexpected source state) suggests its state may have changed.

   Do not re-verify every prior completed feature on every invocation.

A session summary or memory note is contextual guidance, not a substitute for reading current source.
