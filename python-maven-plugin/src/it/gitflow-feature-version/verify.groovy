def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

assert log =~ /Python version: 1\.0\.0\.dev\d{14}\+feature\.login \(from Maven version 1\.0\.0-feature_login-SNAPSHOT\)/
def dists = new File(basedir, "target/python-build").list().findAll { !it.startsWith(".") }.sort()
assert dists.size() == 2, dists
assert dists.every { it ==~ /it_pkg-1\.0\.0\.dev\d{14}\+feature\.login(\.tar\.gz|-py3-none-any\.whl)/ }, dists
