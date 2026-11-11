String log = new File(basedir, "build.log").text
assert log.contains("no OCI image layout found")
assert log.contains("run agent-skills:build before agent-skills:push")
