def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

def check = log.readLines().find { it.contains("Running command:") && it.contains("'check'") }
assert check, "no ruff check command in the debug log"
assert check.contains("'--extend-select' 'E,F,N'"), check
assert check.contains("'--config' 'format.quote-style = \"single\"'"), check
assert check.contains("'--config' 'line-length = 100'"), check
assert !check.contains("line-length = 79"), check

contains("All checks passed!")
contains("ruff found files that aren't formatted. Run `ruff format` to fix them.")
