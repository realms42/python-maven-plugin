# python-maven-plugin

A Maven plugin that builds, tests, lints and packages Python projects, so they fit into Maven builds, parent POMs
and repositories like any other module. It uses [uv](https://docs.astral.sh/uv/) for environments and builds,
pytest for tests and ruff for linting and formatting, and works from the project's own `pyproject.toml`: the same
project builds and runs without Maven too.

## Getting started

**Requirements**

- JDK 11 or later and Maven 3.9 (tested with 3.9.11).
- [uv](https://docs.astral.sh/uv/) on `PATH` (tested with 0.11 and 0.13). uv finds or downloads a Python that
  matches the project's `requires-python`.
- The project's own `pyproject.toml`, with a build backend, and a committed `uv.lock` (run `uv lock`).

The plugin isn't released yet: install it into your local Maven repository with `task build`, or `mvn install` in
`python-maven-plugin/`.

**1. Declare the plugin** with `<packaging>python</packaging>` and `<extensions>true</extensions>`:

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>my-package</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>python</packaging>

  <build>
    <plugins>
      <plugin>
        <groupId>com.github.realms42</groupId>
        <artifactId>python-maven-plugin</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <extensions>true</extensions>
      </plugin>
    </plugins>
  </build>
</project>
```

**2. Let the build backend take its version from Maven**, and from git without Maven. With hatchling and
hatch-vcs (see [Versioning](#versioning) for why and for other backends):

```toml
[project]
name = "my_package"
requires-python = ">=3.9"
dynamic = ["version"]

[build-system]
requires = ["hatchling", "hatch-vcs"]
build-backend = "hatchling.build"

[tool.hatch.version]
source = "vcs"
fallback-version = "0.0.0"

[tool.uv]
cache-keys = [{ file = "pyproject.toml" }, { git = { commit = true, tags = true } }]
```

**3. Build:** `mvn install` builds, tests, packages, lints and checks the formatting, then installs the sdist and
wheel into the local Maven repository. `example_parent/` and `example_package/` are a complete example: the
parent holds the plugin and its configuration, the package only names itself, and `mvn install` in
`example_parent/` builds both.

## The build

`<packaging>python</packaging>` binds the plugin's goals to Maven's lifecycle:

| Phase | Goal | Does | Skip with |
|---|---|---|---|
| compile | `build` | `uv build` of the sdist and wheel into `target/python-build/`, with the Maven version | |
| test | `test` | pytest against the built wheel, with a JUnit report in `target/surefire-reports/` | `-DskipTests`, `-Dmaven.test.skip=true` |
| package | `package` | the sdist becomes the Maven artifact; each wheel is attached with its tag as classifier | |
| verify | `lint` | `ruff check`, never `--fix` | `-Dpython.lint.skip` |
| verify | `format` | `ruff format --check`, never rewrites a file | `-Dpython.format.skip` |
| install, deploy | Maven's install and deploy plugins | | |

`publish`, which uploads to a Python package index, isn't bound; see [Publishing](#publishing).

- **The build never modifies the project.** Every environment, cache and output goes under
  `target/python-maven-plugin/` and `target/python-build/`, and ruff never fixes or rewrites anything.
- **Tests run against the built wheel**, installed in place of the source, so they test what is packaged: a module
  or data file the wheel leaves out fails them. The wheel carries the Maven version. `-Dpython.test.wheel=false` tests the source tree instead, as an
  editable install. With a `src/` layout, tests can only import the installed wheel; with a flat layout, a
  `conftest.py` at the project root lets pytest import the source directory instead.
- **The lock is respected.** The test environment is synced with `uv sync --locked`, which fails on a missing or
  stale `uv.lock` instead of updating it.
- **Failures are reported the Maven way.** Lint violations, unformatted files and failing tests fail the build
  with a message saying which; a tool that can't run (a bad configuration, a stale lock) is a build error naming
  the command.
- **`mvn -o`** keeps uv offline too, working from its cache. **Parallel builds** (`-T`) are supported.
- `mvn help:describe -Dplugin=com.github.realms42:python-maven-plugin -Ddetail` lists every goal and parameter.

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

Gitflow and git-based versioning put the branch name in feature branch versions: gitflow-maven-plugin's
`feature-start` makes `1.0.0-SNAPSHOT` into `1.0.0-JIRA-42_Add-Login-SNAPSHOT`, and jgitver gives a
`feature/JIRA-42_add-login` branch `1.0.1-feature_jira_42_add_login-SNAPSHOT`. Both build, as
`1.0.0.dev…+jira.42.add.login` and `1.0.1.dev…+feature.jira.42.add.login`.
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

The optional `publish` goal uploads the built distributions with `uv publish`, to the package index the
configuration names:

- **There is no default repository.** Releases and snapshots each need a configured URL, so nothing is uploaded
  anywhere unless a POM or `-D` says where.
- **It isn't bound to the lifecycle**, so `mvn deploy` doesn't run it unless a POM adds an execution.
- **Every file is validated with a dry run** before anything is uploaded.
- **It fails fast under `mvn -o`**, before uv runs.

Releases and `-SNAPSHOT` versions go to separate repositories, written like `<distributionManagement>`
repositories. The `id` names the `settings.xml` `<server>` that holds the credentials (encrypted passwords work):

```xml
<plugin>
  <groupId>com.github.realms42</groupId>
  <artifactId>python-maven-plugin</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <extensions>true</extensions>
  <configuration>
    <releaseRepository>
      <id>pypi-releases</id>
      <url>https://nexus.example.com/repository/pypi-releases/</url>
    </releaseRepository>
    <snapshotRepository>
      <id>pypi-snapshots</id>
      <url>https://nexus.example.com/repository/pypi-snapshots/</url>
    </snapshotRepository>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>publish</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

With that execution it runs in the `deploy` phase; without it, run `mvn python:publish` after a build. Each value
can also come from a property, for example in a parent POM's `<properties>` or a `settings.xml` profile; a
configuration block wins value by value:

| Property | Meaning |
|---|---|
| `python.publish.releaseRepository.id`, `.url` | The release repository |
| `python.publish.snapshotRepository.id`, `.url` | The snapshot repository |
| `python.publish.skipReleases`, `python.publish.skipSnapshots` | Skip publishing releases, or snapshots |
| `python.publish.skip` | Skip publishing entirely |

Without an `id`, uv finds credentials itself (`UV_PUBLISH_USERNAME` and `UV_PUBLISH_PASSWORD`, or trusted
publishing). Snapshot versions are timestamped and feature branch versions carry a `+branch` label; see
[Versioning](#versioning) for what an index has to accept.

`mvn deploy` still runs Maven's own deploy plugin, as for any Maven project: it uploads the sdist and wheel as Maven
artifacts to the POM's `<distributionManagement>` repository, and fails if there is none.

**Safeguard in the example:** `example_parent/` points both repositories at PyPI's staging index, TestPyPI, so
the only place a publish from it can go is staging, never PyPI itself. It binds `publish` to `deploy`, but Maven's
own deploy step runs first in that phase and fails, as there is no `<distributionManagement>`, so `mvn deploy`
stops before publishing; `mvn python:publish` publishes to TestPyPI, with credentials for the `testpypi` server.
Files on TestPyPI are public, so a real setup points the repositories at its own index.

## Building an RPM

`example_rpm/` packages the example's wheel as an RPM. It takes the wheel as an ordinary Maven dependency
(`<type>whl</type>`, `<classifier>py3-none-any</classifier>`), runs `rpmbuild` on
`src/main/rpm/python3-example-package.spec`, and attaches the RPM to the build as an artifact of type `rpm`.

The spec installs the wheel as it is, without rebuilding it, using Fedora's Python macros rather than hard-coded
paths: `%pyproject_install` from `%{_pyproject_wheeldir}`, `%pyproject_save_files` for the file list,
`%pyproject_check_import` as the check, and `%{python3}`. Maven stores the wheel under its Maven name, which pip
won't install, so `wheel_info.py` reads the real file name and version from the wheel's metadata. The RPM version
is the wheel's, with pre-releases written Fedora's way: `1.0.0.dev20261109092243` becomes
`1.0.0~dev20261109092243`.

It needs `rpm-build`, `python3-devel`, `pyproject-rpm-macros` and `python3-pip`, as on Fedora, so it is only
built with the `rpm` profile: `task rpm`, or `mvn install -Prpm` in `example_parent/`. CI builds it in a Fedora
container, installs the RPM and imports the package from it.

## Configuring many projects from a parent POM

Everything the plugin applies to a project is a Maven parameter, so one parent POM can configure every project
below it, and changing a policy never needs a new plugin release. `example_parent/pom.xml` does this for the
example. Put the configuration in the parent's `<pluginManagement>`, or, as the example does, declare the plugin
with `<extensions>true</extensions>` in the parent's `<build><plugins>` so that children inherit it outright:

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
          <!-- Any pytest option, for test. Keys are pytest's configuration options, as in
               [tool.pytest.ini_options]; values are plain text, as pytest reads them. -->
          <pytestSettings>
            <xfail_strict>true</xfail_strict>
            <addopts>--strict-markers</addopts>
          </pytestSettings>
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
| `pytestSettings` | `test` | none |
| `ruffVersion` | `lint`, `format` | 0.17.3 |
| `pytestVersion` | `test` | 9.2.0 |

- **`ruffSettings` and `pytestSettings` override the project's own value** for each option they name: ruff gets
  them as `--config "key = value"` overrides, pytest as `-o key=value` overrides, both on top of the project's
  configuration. Every option they don't name stays the project's, and running ruff or pytest outside Maven uses
  the project's configuration alone. A pytest `addopts` entry replaces the project's `addopts`.
- **A child POM can override single entries.** Maven merges a child's `<ruffSettings>` or `<pytestSettings>` with
  its parent's entry by entry, so a child that sets only `<line-length>120</line-length>` keeps the parent's other
  settings. To replace the parent's whole map, use `<ruffSettings combine.self="override">`.
- **A malformed value fails the build** with the tool's own error, as a build error rather than as lint violations
  or test failures. pytest only warns about an option it doesn't know.
- **The plugin's own pytest options win:** its cache and JUnit report stay under `target/` whatever the settings
  say.

## Developing the plugin

`task build` (or `mvn install` in `python-maven-plugin/`) runs the unit tests and the integration tests.

- **Unit tests** (`src/test`) fake every process the plugin starts, so they need no uv, Python or network.
- **Integration tests** (`src/it`) are small Python projects that real Maven builds with the plugin, using real uv,
  ruff and pytest; each one's `verify.groovy` then checks the build log and its output. They need `uv` on `PATH`
  and network access to PyPI for the tools and build backends. They cover what faked processes can't: how Maven
  injects parameters, parent POM inheritance, reactor builds, and the tools' real exit codes.
  - `task it` runs only them, and `task it -- -Dinvoker.test=lint-violation` runs one.
  - `-Dinvoker.skip` skips them, for example to build offline.
  - A test project's `uv.lock` is committed; regenerate it with `uv lock` in its directory after changing its
    `pyproject.toml`. `stale-lock` keeps a deliberately outdated one.

The Docker image (`task docker`) only builds: the plugin, then the example, without tests, integration tests or
checks, which run in CI.

## Releasing the plugin

The plugin is prepared for a Maven release but hasn't been released. Prepared:

- **The metadata Maven Central expects:** name, description, URL, license (GPL-3.0, as `LICENSE`), developer,
  source repository and issue tracker.
- **A `release` profile**, used by `release:perform`, that also builds the sources and Javadoc jars.
  `mvn -P release verify` builds them.
- **`maven-release-plugin`**, set to tag `v<version>` and to push nothing itself.
- **Deployment to Maven Central's staging only.** The `release` profile deploys with Sonatype's
  `central-publishing-maven-plugin` and `autoPublish` off: a deployment is uploaded to the Central Portal and
  validated, and nothing is published until someone presses Publish for it on central.sonatype.com.

Safeguards, so that a deployment that fires by accident publishes nothing:

- **A plain `mvn deploy` fails:** there is no `<distributionManagement>`, so it has nowhere to upload.
- **Only release versions are deployed.** Central publishes `-SNAPSHOT` versions to its snapshot repository
  straight away, without staging, so in the `release` profile the Maven Enforcer Plugin refuses snapshot versions
  in the `deploy` phase, before the upload.
- **Staging, not publishing,** for release versions, as above.

Still needed before a first release to Central:

- **A verified namespace.** Central registers GitHub-based namespaces as `io.github.<user>`, so the groupId would
  likely change from `com.github.realms42`.
- **Signed artifacts:** Central rejects unsigned deployments at validation; add `maven-gpg-plugin` to the
  `release` profile with a signing key.
- **Credentials:** a `settings.xml` `<server>` with the id `central` holding a Central Portal user token.

To release, in `python-maven-plugin/`:

1. **Check:** `mvn release:prepare -DdryRun=true`, then `mvn release:clean`. This builds and tests the release
   version and shows the POMs it would commit, without committing or tagging anything.
2. **Prepare:** `mvn release:prepare` sets the release version, builds and tests it (including the integration
   tests), commits it, tags it `v<version>` and sets the next `-SNAPSHOT`, all locally.
3. **Push:** `git push --follow-tags`, once the commits and tag look right.
4. **Perform:** `mvn release:perform` builds the tag with the `release` profile and uploads it to Central's
   staging.
5. **Publish:** review the deployment on central.sonatype.com and press Publish, or drop it.
6. **Follow up:** point `example_parent/pom.xml` and this README at the released version.
