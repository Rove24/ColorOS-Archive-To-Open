# Vendored libxposed API sources

`java/io/github/libxposed/api/**` is copied verbatim from <https://github.com/libxposed/api>
at commit `79b75b4` (tag `102.0.0` plus three commits), and `LICENSE` is the upstream Apache-2.0
license.

The module compiles these sources directly instead of resolving
`io.github.libxposed:api:102.0.0` from Maven, so the repository builds without network access to
the artifact and without an out-of-tree checkout. `app` depends on this module with
`compileOnly(project(":libxposed-api"))`, matching the upstream guidance that the framework
provides the API at runtime.

When updating, re-copy `api/src/main/java` from upstream and keep this note's commit reference in
sync.
