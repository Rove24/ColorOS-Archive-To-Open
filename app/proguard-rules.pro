# The entry class is named verbatim in META-INF/xposed/java_init.list, so it must survive
# any future obfuscation pass untouched.
-keep class io.github.andrea_lyz.archivetoopen.ArchiveToOpenModule { *; }
