def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

// uv.lock no longer matches pyproject.toml: a build error naming uv, never reported as test failures.
contains("`uv sync` failed with exit code")
assert !log.contains("There are test failures")
