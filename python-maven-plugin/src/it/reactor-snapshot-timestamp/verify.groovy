def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

def versions = (log =~ /Python version: (\S+) \(from Maven version 1\.0\.0-SNAPSHOT\)/).collect { it[1] }
assert versions.size() == 2, versions
assert versions[0] ==~ /1\.0\.0\.dev\d{14}/, versions
assert versions[0] == versions[1], versions
