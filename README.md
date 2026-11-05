# python-maven-plugin

## Versioning

The distributions Maven builds get the Maven version, converted to PEP 440, the version format Python packaging
requires. The `build` goal logs the result:

| Maven version | Python version |
|---|---|
| `1.0.0` | `1.0.0` |
| `1.0.0-SNAPSHOT` | `1.0.0.dev20261105152300`: the build's start time, UTC |
| `1.0.0-alpha-1`, `-beta-2`, `-rc-3` | `1.0.0a1`, `1.0.0b2`, `1.0.0rc3` |
| `1.0.0.Final`, `.RELEASE`, `-GA` | `1.0.0` |
| `1.0.0-feature_name-SNAPSHOT` | `1.0.0.dev20261105152300+feature.name` |
| `1.0.0-M1` | rejected: Python has no milestones; use `-alpha-1` or `-rc-1` |

Gitflow and git-based versioning (gitflow-maven-plugin, jgitver) put the branch name in feature branch versions.
The only place PEP 440 allows such text is the local version label after the `+`: lowercased, with every run of
other characters turned into a `.`. It keeps builds of different branches apart, and sorts right after the base
version, before any pre-release. A qualifier only counts as a pre-release when it is nothing else, so a branch
named `rc-fixes` or `a-team` stays a branch. Indexes that follow PyPI's rules reject local versions, so feature
branch builds can only be published to an index that accepts them.

A snapshot's dev number is the time the Maven build started, in UTC. Every goal and every module of one build gets
the same one, and each new build a later one, so snapshots can be published again and again: package indexes
refuse a second upload of the same version, which a fixed `.dev0` would hit on the second publish.

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
- **Other settings** (`line-length`, formatter options, excludes, ...) are the project's own, unless `ruffSettings`
  names them (see below).
- **Changing the base** is done with the `enforcedRules` parameter, usually in a parent POM (see below), or with
  `-Dpython.lint.enforcedRules=E,F,S`. An empty value lints with the project's own configuration alone.

## Publishing

The optional `publish` goal uploads the built distributions with `uv publish`, to **private** package indexes only:

- **Public PyPI and TestPyPI are always refused** (`pypi.org`, `test.pypi.org`, `upload.pypi.org`, `pythonhosted.org`),
  whatever the configuration says, before uv runs.
- **There is no default repository.** Releases and snapshots each need a configured URL, so nothing is uploaded
  anywhere unless a POM or `-D` says where.
- **It isn't bound to the lifecycle**, so `mvn deploy` doesn't run it unless a POM adds an execution.
- **Every file is validated with a dry run** before anything is uploaded.

`mvn deploy` still runs Maven's own deploy plugin, as for any Maven project: it uploads the sdist and wheel as Maven
artifacts to the POM's `<distributionManagement>` repository, and fails if there is none.

## Configuring many projects from a parent POM

Everything the plugin applies to a project is a Maven parameter, so one parent POM can configure every project
below it, and changing a policy never needs a new plugin release. Put the configuration in the parent's
`<pluginManagement>`:

```xml
<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>com.github.realms42</groupId>
        <artifactId>python-maven-plugin</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <configuration>
          <!-- The base ruleset: added to each project's own rules, can't be switched off by them. -->
          <enforcedRules>
            <rule>E</rule>
            <rule>F</rule>
            <rule>B</rule>
          </enforcedRules>
          <!-- Any other ruff setting, for lint and format. Keys are ruff's setting names, dotted for nested
               sections; values are TOML, so strings need quotes. -->
          <ruffSettings>
            <line-length>100</line-length>
            <target-version>"py311"</target-version>
            <format.quote-style>"double"</format.quote-style>
          </ruffSettings>
          <!-- The tool versions the plugin runs. -->
          <ruffVersion>0.17.3</ruffVersion>
          <pytestVersion>9.2.0</pytestVersion>
        </configuration>
      </plugin>
    </plugins>
  </pluginManagement>
</build>
```

| Parameter | Applies to | Default |
|---|---|---|
| `enforcedRules` | `lint` | `E, W, F, I, B, UP` |
| `ruffSettings` | `lint`, `format` | none |
| `ruffVersion` | `lint`, `format` | 0.17.3 |
| `pytestVersion` | `test` | 9.2.0 |

- **`ruffSettings` override the project's own value** for each setting they name, because ruff applies them as
  `--config` overrides on top of the project's configuration. Every setting they don't name stays the project's,
  and running ruff outside Maven uses the project's configuration alone.
- **A child POM can override single entries.** Maven merges a child's `<ruffSettings>` with its parent's entry by
  entry, so a child that sets only `<line-length>120</line-length>` keeps the parent's other settings. To replace
  the parent's whole map, use `<ruffSettings combine.self="override">`.
- **A malformed value fails the build** with ruff's own error, as a build error rather than as lint violations.
