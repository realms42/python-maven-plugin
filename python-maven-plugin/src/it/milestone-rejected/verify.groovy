def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

contains("Can't convert the Maven version 1.0.0-M1 to a Python (PEP 440) version: Python has no milestone releases.")
assert !new File(basedir, "target/python-build").exists()
