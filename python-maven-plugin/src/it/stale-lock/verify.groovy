def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

contains("`uv sync` failed with exit code")
assert !log.contains("There are test failures")
