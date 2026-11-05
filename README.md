# python-maven-plugin

## Versioning

The distributions Maven builds get the Maven version, converted to PEP 440, the version format Python packaging
requires. The `build` goal logs the result:

| Maven version | Python version |
|---|---|
| `1.0.0` | `1.0.0` |
| `1.0.0-SNAPSHOT` | `1.0.0.dev0` |
| `1.0.0-alpha-1`, `-beta-2`, `-rc-3` | `1.0.0a1`, `1.0.0b2`, `1.0.0rc3` |
| `1.0.0.Final`, `.RELEASE`, `-GA` | `1.0.0` |
| `1.0.0-feature_name-SNAPSHOT` | `1.0.0.dev0+feature.name` |
| `1.0.0-M1` | rejected: Python has no milestones; use `-alpha-1` or `-rc-1` |

Gitflow and git-based versioning (gitflow-maven-plugin, jgitver) put the branch name in feature branch versions.
The only place PEP 440 allows such text is the local version label after the `+`: lowercased, with every run of
other characters turned into a `.`. It keeps builds of different branches apart, and sorts right after the base
version, before any pre-release. A qualifier only counts as a pre-release when it is nothing else, so a branch
named `rc-fixes` or `a-team` stays a branch. PyPI, and indexes that follow its rules, reject local versions, so
feature branch builds can only be published to an index that accepts them.

The plugin passes the version to `uv build` through the variables that version-from-git backends already read,
so a project needs no Maven-specific configuration:

| Variable | Read by |
|---|---|
| `SETUPTOOLS_SCM_PRETEND_VERSION` | hatch-vcs, setuptools-scm |
| `PDM_BUILD_SCM_VERSION` | pdm-backend |

Outside Maven, the project's own versioning applies, so `pyproject.toml` works on its own. With hatchling:

```toml
[build-system]
requires = ["hatchling", "hatch-vcs"]
build-backend = "hatchling.build"

[project]
dynamic = ["version"]

# The version comes from git tags, and falls back to 0.0.0 outside a git checkout.
[tool.hatch.version]
source = "vcs"
fallback-version = "0.0.0"

# The version depends on git, so uv must rebuild the project when the commit or tags change.
[tool.uv]
cache-keys = [{ file = "pyproject.toml" }, { git = { commit = true, tags = true } }]
```

| | In a git checkout | Outside git |
|---|---|---|
| **Without Maven** | from git tags, e.g. `1.0.1.dev3+g1a2b3c4` | the fallback, `0.0.0` |
| **Maven-built distributions** | the Maven version | the Maven version |

The variables are only set for `uv build`, which builds the project alone. They apply to every package a build
backend builds, so setting them while syncing the environment for `test` would also give the project's version to
any dependency built from source there, such as a path or git dependency. Tests therefore run against the
project's own version (from git, or the fallback), and only the distributions carry the Maven version.

If the project lives in a subdirectory of its git repository, add
`raw-options = { search_parent_directories = true }` to `[tool.hatch.version]`.

## Tool versions

Under Maven, the plugin runs its own pinned versions of the tools it uses, whatever the project pins:

| Tool | Version | Goals | Change it with |
|---|---|---|---|
| ruff | 0.17.3 | `lint`, `format` | `python.ruff.version` |
| pytest | 9.2.0 | `test` | `python.pytest.version` |

A new release of either can't break a build that changed nothing. Upgrading the plugin upgrades them on purpose.
Both properties take an exact version, set in a POM's `<properties>` or with `-D`.

The plugin's environments live under `target/python-maven-plugin/`:

- **ruff** runs from its own environment there, and doesn't need the project installed.
- **pytest** runs in the plugin's environment for the project, synced from `uv.lock`, so test dependencies and
  pytest plugins come from the project. The pinned pytest is installed over whatever version the lock has.

Running the tools yourself, outside Maven, is separate from all of this. Declare them as dependencies in your own
`pyproject.toml`, for example in the `dev` dependency group, and configure them there. `uv run ruff` and
`uv run pytest` then use your versions and your `.venv`. Maven never uses or touches that `.venv`, and your own
runs don't pick up anything from `target/`, whether or not your `.gitignore` lists it.

## Ruff rules

The `lint` goal checks every project against a base ruleset, `E, W, F, I, B, UP`. Projects add to it in their own
ruff configuration, in `pyproject.toml` or `ruff.toml`:

```toml
# pyproject.toml
[tool.ruff]
line-length = 100

[tool.ruff.lint]
extend-select = ["N"]

[tool.ruff.lint.per-file-ignores]
"tests/*" = ["B"]
```

- **Outside Maven**, `ruff` reads that configuration as usual, so running it by hand uses the project's settings.
- **Under Maven**, the base rules are added on top of the project's configuration with `--extend-select`. The
  project's `select` and `ignore` can't switch them off. Use `per-file-ignores` or `# noqa` for justified exceptions.
- **Other settings** (`line-length`, formatter options, excludes, ...) are the project's own; the plugin sets none.
- **Changing the base** is done with the `enforcedRules` parameter, for example in a parent POM so that it applies
  to every project below it. An empty value lints with the project's own configuration alone.

```xml
<configuration>
  <enforcedRules>
    <rule>E</rule>
    <rule>F</rule>
    <rule>S</rule>
  </enforcedRules>
</configuration>
```

It can also be set with `-Dpython.lint.enforcedRules=E,F,S`.
