# Android skills

Reusable Claude Code skills for Android/Kotlin projects. Each skill is split in two:

| File | Holds | Changes per project |
|---|---|---|
| `<skill>/SKILL.md` and its generic `references/*.md` | The rules, workflow, pitfalls and templates, written with role names (`BaseViewModel`, `DataResult<T>`, `AppTheme`…) | No |
| `<skill>/references/project.md` (and `project-mapping.md` for the Compose migration) | The **project profile**: a match check, then a table *Role or item / How to find it / This project* | Yes |

| Skill | For |
|---|---|
| `commit` | Conventional commits, scope = Gradle module |
| `feature-architecture` | ui / domain / data layers, MVI on `UiState<T>` + `onIntent`, Compose screens behind a Fragment or a Navigation Compose route, naming |
| `android-unit-testing` | JUnit tests for ViewModels, use cases, repositories, mappers |
| `live-data-migration` | One ViewModel from LiveData to `StateFlow` / `Channel` |
| `migrate-xml-views-to-jetpack-compose` | One XML layout to Compose on the project's design system |
| `android-dagger-to-hilt` | Incremental Dagger 2 → Hilt migration |

The profiles in this folder describe **NetScope**. A role the project lacks is marked *none*; a skill whose whole
subject is absent here (LiveData, XML layouts, Dagger) says so at the top of its profile and stops.

## Using them in another project

1. Copy the skill folders into the project's `.claude/skills/` (or into `~/.claude/skills/` to have them everywhere).
2. Ask Claude to set them up: *"rebuild the project profiles of the skills for this repository"*. Each profile
   starts with a match check that fails in the new repository, so Claude rewrites the "This project" column from
   the "How to find it" column (greps, the settings file, the reference feature), marks missing building blocks
   *none*, and shows the result. A skill used before that step does the same on first use.
3. Review the profiles: they are the only place a project's names live. Keep them up to date when a base class,
   module or command changes.

Rules that are house style rather than project facts (named arguments, `operator fun invoke`, dependencies named
after their type, `should … when …` test names, no `Co-Authored-By` trailer) live in the generic files on purpose:
change them there if a project's team decides otherwise.
