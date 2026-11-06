def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

// The whole python lifecycle ran: build, test, package, lint, format, install.
contains("BUILD SUCCESS")
contains("All checks passed!")
contains("already formatted")

// The distributions carry the Maven version, a snapshot timestamped with the build's start time.
// uv build also writes a .gitignore there.
def dists = new File(basedir, "target/python-build").list().findAll { !it.startsWith(".") }.sort()
assert dists.size() == 2, dists
assert dists.every { it ==~ /it_pkg-1\.0\.0\.dev\d{14}(\.tar\.gz|-py3-none-any\.whl)/ }, dists

// Tests ran with a JUnit report where CI servers look for one.
def report = new File(basedir, "target/surefire-reports/TEST-pytest.xml")
assert report.isFile()
assert report.text =~ /tests="1"/

// The plugin's environments are under target/, and the project's own .venv is never created.
assert new File(basedir, "target/python-maven-plugin/tools/.venv").isDirectory()
assert new File(basedir, "target/python-maven-plugin/.venv").isDirectory()
assert !new File(basedir, ".venv").exists()

// The sdist is the main artifact and the wheel is attached with its compatibility tag.
def installed = new File(localRepositoryPath, "it/build-lifecycle/1.0.0-SNAPSHOT")
assert new File(installed, "build-lifecycle-1.0.0-SNAPSHOT.tar.gz").isFile()
assert new File(installed, "build-lifecycle-1.0.0-SNAPSHOT-py3-none-any.whl").isFile()
