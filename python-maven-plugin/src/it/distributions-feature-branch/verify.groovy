def log = new File(basedir, "build.log").text

def wheel = (log =~ /wheel\.file=(it_pkg-1\.0\.0\.dev(\d{14})\+feature\.login-py3-none-any\.whl)/)
assert wheel.find(), "the wheel's file property didn't reach the later plugin"
def timestamp = wheel.group(2)
assert log.contains("wheel.version=1.0.0.dev" + timestamp + "+feature.login")
assert log.contains("wheel.rpmVersion=1.0.0~~dev" + timestamp + "+feature.login")

def sdist = "it_pkg-1.0.0.dev" + timestamp + "+feature.login.tar.gz"
assert log.contains("sdist.file=" + sdist)
assert log.contains("sdist.rpmVersion=1.0.0~~dev" + timestamp + "+feature.login")

def output = new File(basedir, "consumer/target/distributions")
assert new File(output, wheel.group(1)).isFile()
assert new File(output, sdist).isFile()
