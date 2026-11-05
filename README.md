# python-maven-plugin

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
