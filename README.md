# python-maven-plugin

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
