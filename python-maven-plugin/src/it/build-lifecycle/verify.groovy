def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

contains("BUILD SUCCESS")
contains("All checks passed!")
contains("already formatted")

def dists = new File(basedir, "target/python-build").list().findAll { !it.startsWith(".") }.sort()
assert dists.size() == 2, dists
assert dists.every { it ==~ /it_pkg-1\.0\.0\.dev\d{14}(\.tar\.gz|-py3-none-any\.whl)/ }, dists

contains("Testing the built wheel: it_pkg-1.0.0.dev")
def report = new File(basedir, "target/surefire-reports/TEST-pytest.xml")
assert report.isFile()
assert report.text =~ /tests="2"/
assert report.text =~ /failures="0"/

assert new File(basedir, "target/python-maven-plugin/tools/.venv").isDirectory()
assert new File(basedir, "target/python-maven-plugin/.venv").isDirectory()
assert !new File(basedir, ".venv").exists()

def installed = new File(localRepositoryPath, "it/build-lifecycle/1.0.0-SNAPSHOT")
assert new File(installed, "build-lifecycle-1.0.0-SNAPSHOT.tar.gz").isFile()
assert new File(installed, "build-lifecycle-1.0.0-SNAPSHOT-py3-none-any.whl").isFile()
