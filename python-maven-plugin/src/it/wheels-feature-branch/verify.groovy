def log = new File(basedir, "build.log").text

def file = (log =~ /file=(it_pkg-1\.0\.0\.dev(\d{14})\+feature\.login-py3-none-any\.whl)/)
assert file.find(), "the file property didn't reach the later plugin"
def timestamp = file.group(2)
assert log.contains("version=1.0.0.dev" + timestamp + "+feature.login")
assert log.contains("rpmVersion=1.0.0~~dev" + timestamp + "+feature.login")
assert new File(basedir, "consumer/target/wheels/" + file.group(1)).isFile()
