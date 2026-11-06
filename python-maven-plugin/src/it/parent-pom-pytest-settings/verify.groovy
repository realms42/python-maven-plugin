def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

// The child's pytest run got the parent's settings, with its own console_output_style in place of the parent's.
def pytest = log.readLines().find { it.contains("Running command:") && it.contains("/bin/pytest'") }
assert pytest, "no pytest command in the debug log"
assert pytest.contains("'-o' 'xfail_strict=true'"), pytest
assert pytest.contains("'-o' 'console_output_style=count'"), pytest
assert !pytest.contains("console_output_style=classic"), pytest

// The parent's xfail_strict takes effect: a test marked xfail that passes fails the build.
contains("XPASS(strict)")
contains("There are test failures. See pytest's output above.")
