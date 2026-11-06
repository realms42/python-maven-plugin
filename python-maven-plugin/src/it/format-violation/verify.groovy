def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

contains("All checks passed!")
contains("ruff found files that aren't formatted. Run `ruff format` to fix them.")
