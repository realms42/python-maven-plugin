def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

contains("There are test failures. See pytest's output above.")
assert new File(basedir, "target/surefire-reports/TEST-pytest.xml").text =~ /failures="1"/
