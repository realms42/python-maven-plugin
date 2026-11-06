def log = new File(basedir, "build.log").text
def contains = { String s -> assert log.contains(s), "build.log does not contain: " + s }

// The first build tested the wheel, which lacks greeting.txt, and failed; the second tested the source and passed.
contains("Testing the built wheel: it_pkg-1.0.0.dev")
contains("greeting.txt")
contains("There are test failures. See pytest's output above.")
contains("Testing the source tree.")
contains("BUILD SUCCESS")
