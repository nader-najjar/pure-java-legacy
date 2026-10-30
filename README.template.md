# pure-java-legacy

> Java best practices.

### 1. Prerequisites

---

- macOS or Linux on ARM64 or x86-64, as configured in `flake.nix`.
- Install [Nix](https://nix.dev/install-nix).
- Network access for uncached toolchains and dependencies.

Run the commands below from the repository root.

### 2. Build and Test

---

#### Everyday Commands

| Goal | Command |
| --- | --- |
| Compile production Java only | `./universal-build compileJava` |
| Compile production and test Java without running tests | `./universal-build testClasses` |
| Run all unit tests, without lint or packaging | `./universal-build test` |
| Run one test class | `./universal-build test --tests 'io.template.samplebusinesslayer.CalculatorTest'` |
| Run one test method | `./universal-build test --tests 'io.template.samplebusinesslayer.CalculatorTest.calculatesAddition'` |
| Check source style | `./universal-build checkstyleMain checkstyleTest` |
| Run static bug analysis | `./universal-build spotbugsMain spotbugsTest` |
| Generate coverage report | `./universal-build jacocoTestReport` |
| Verify all checks without packaging | `./universal-build check` |
| Assemble runnable JARs without checks | `./universal-build installDist` |
| Package release without checks | `./universal-build packageRelease` |
| Full verification and packaging | `./universal-build build` |
| Remove build outputs | `./universal-build clean` |
| List available tasks | `./universal-build tasks --all` |

Tasks run their prerequisites: tests and SpotBugs compile needed classes; packaging compiles and assembles runtime dependencies. `build` runs tests, the 80% coverage gate, Checkstyle, SpotBugs, and Javadoc checks, and produces release artifacts. It does not build an image or test a deployment.

Gradle skips up-to-date work. Useful command flags:

- `--rerun-tasks`: force the selected tasks and their prerequisites to execute.
- `--dry-run`: show the task graph without executing tasks.
- `--stacktrace`: show failure details.

#### Outputs

- Test results: `build/reports/tests/test/index.html`.
- Coverage: `build/reports/jacoco/test/html/index.html` after `jacocoTestReport`; the coverage gate alone does not generate this report.
- Checkstyle and SpotBugs: `build/reports/checkstyle/` and `build/reports/spotbugs/`.
- Runnable application and dependencies: `build/release/executable/lib/`.
- Release archive: `build/release-artifact.tar.gz`, containing the runtime JARs and image-build inputs.

### 3. Development Workflows

---

#### Dependency Management

Edit `build.gradle.kts`:

- Add: `implementation("group:artifact:version")` (`testImplementation` for tests).
- Remove: delete the declaration and its usages.
- Upgrade/pin: change the version, or the BOM/constraint that supplies it.

Refresh locks after adding or removing dependencies:

```sh
./universal-build dependencies --write-locks
```

For a targeted version update, replace `group:artifact`:

```sh
./universal-build dependencies --update-locks group:artifact
```

Inspect a selected version or conflict:

```sh
./universal-build dependencyInsight --dependency group:artifact --configuration runtimeClasspath
```

Use `testRuntimeClasspath` for test dependencies.

#### Toolchain Changes

| Change | Files and steps |
| --- | --- |
| Java version | Change `chosenJdk` in `flake.nix` and `JavaLanguageVersion.of(...)` in `build.gradle.kts` together. The runtime shell uses the same JDK. |
| Add a CLI tool | Add its Nix package to the development shell's `packages` in `flake.nix`. |
| Nix package set | Change `inputs.nixpkgs.url` in `flake.nix`, then run `./universal-build --update-nix-flake-lockfile`. Review and commit `flake.nix` and `flake.lock`. |
| Gradle version | Run `./universal-build wrapper --gradle-version X.Y.Z`, then `./universal-build wrapper` to refresh wrapper files with the new version. Review `gradle/wrapper/`, `gradlew`, and `gradlew.bat`. |
| Build plugin or analysis tool | Change its plugin version or `toolVersion` in `build.gradle.kts`; regenerate dependency locks if its resolved dependencies change. |

After toolchain changes, run `./universal-build build` and refresh the IDE's JDK path.

### 4. IDE Setup

---

Print the pinned JDK location:

```sh
./universal-build --print-java-path-for-ide
```

Use that path for the project SDK and Gradle JVM. Import the project as Gradle and use its wrapper, not a separately installed Gradle.

- [IntelliJ IDEA Ultimate setup](https://app.notion.com/p/f4c0a2b7875583b79fa5015d58412fea)
- [Visual Studio Code setup](https://app.notion.com/p/4fd0a2b787558204a6c001d0b034f698)

Reload Gradle after dependency changes. Reprint the JDK path after Nix/toolchain changes.

### 5. Concepts

---

- [Build and OCI Image Packaging](https://app.notion.com/p/3f90a2b787558157a5c2cf7a9b7e92cc)
- [Product Release Pipelines Across Repositories](https://app.notion.com/p/3f90a2b7875581ef953bca201941e0dd)
- [Deployment Testing and Promotion](https://app.notion.com/p/3f90a2b7875581de812ffaddc1e44b83)
- [Shutdown Concepts](https://app.notion.com/p/4080a2b78755818a93b6c7ffa13a809d)

### 6. Appendix

---

#### Gradle Dependency Reference

Use these inside `dependencies { ... }` in `build.gradle.kts`. Replace `...` with `"group:artifact:version"`; omit the version when a BOM supplies it.

| Declaration | Available when |
| --- | --- |
| `implementation(...)` | Compiling and running production code |
| `compileOnly(...)` | Compiling production code only |
| `runtimeOnly(...)` | Running production code only |
| `annotationProcessor(...)` | Processing annotations during production compilation |
| `testImplementation(...)` | Compiling and running tests |
| `testCompileOnly(...)` | Compiling tests only |
| `testRuntimeOnly(...)` | Running tests only |
| `testAnnotationProcessor(...)` | Processing annotations during test compilation |

These additionally require the `java-library` plugin, which this project does not apply:

| Declaration | Use |
| --- | --- |
| `api(...)` | A library dependency also exposed to consumers at compile time |
| `compileOnlyApi(...)` | Compile-only dependency also needed by consumers to compile |

Other dependency forms:

| Example | Use |
| --- | --- |
| `implementation(platform("group:bom:version"))` | Import a BOM's version constraints |
| `implementation(enforcedPlatform("group:bom:version"))` | Force the BOM's versions, including for consumers; use cautiously |
| `constraints { implementation("group:artifact:version") }` | Constrain a dependency's version without adding it |
| `implementation(project(":module"))` | Depend on another project in the same Gradle build |
| `implementation(files("libs/library.jar"))` | Depend on a local JAR; transitive dependencies are not declared for you |

Reference: [Java configurations](https://docs.gradle.org/current/userguide/java_plugin.html#sec:java_plugin_and_dependency_management), [library API configurations](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph), [dependency types](https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html).

### License

---

All rights reserved. See [LICENSE](LICENSE).
