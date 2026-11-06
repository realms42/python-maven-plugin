def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

contains("BUILD SUCCESS")
assert !log.contains("pytest-8.3.0"), "ran the project's pytest pin"
assert log =~ /pytest-\d+\.\d+\.\d+/
def ruff = ["target/python-maven-plugin/tools/.venv/bin/ruff", "--version"].execute(null, basedir)
def version = ruff.text.trim()
assert ruff.waitFor() == 0
assert version.startsWith("ruff ") && version != "ruff 0.5.0", version
