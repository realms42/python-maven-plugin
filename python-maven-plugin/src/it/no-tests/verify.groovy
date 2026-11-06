def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

contains("No tests to run.")
contains("BUILD SUCCESS")
