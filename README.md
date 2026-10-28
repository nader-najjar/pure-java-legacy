# pure-java-legacy

Java best practices.

&nbsp;

## 1. Local Development Machine Prerequisites

* Install Nix as per <https://nix.dev/install-nix>
  * Nix handles all other requirements so that developer machine setup is as minimal as possible
    * Further, it does not matter what version of Nix you have - it guarantees reproducibility
  * If, after an Apple update, nix is not found on PATH, follow <https://github.com/NixOS/nix/issues/7880> and add the snippet to `~/.zshrc` instead

&nbsp;

## 2. Tips

* Run `echo "alias ub='./universal-build'" >> ~/.zshrc` then `source ~/.zshrc` to make `ub` a quick alias for `./universal-build`
* Add the Nix snippet to your `~/.zshrc` so it is never wiped by system updates (snippet: <https://github.com/NixOS/nix/issues/7880>)

&nbsp;

## 3. Main Build Tasks

### `./universal-build build`

* Compiles the code under `src`
* Runs unit tests
* Runs Jacoco
* Runs Checkstyle
* Runs SpotBugs
* Uses Gradle's Distribution plugin to assemble the application JAR and runtime dependency JARs under `build/release/executable/lib`, without generated launch scripts
* Packages `build/release-artifact.tar.gz` with the runtime JARs and image-build inputs, without constructing an image

### Release archive

The build produces `build/release-artifact.tar.gz`, containing `Dockerfile`, `flake.nix`, `flake.lock`, `.dockerignore`, and `executable/lib/` with the application and runtime dependency JARs. Image packaging can extract this archive directly as its complete build context without recompiling the application.

### `./universal-build clean`

* Removes build output directories

### `./universal-build help`

* Invokes Gradle's help command, showing basic functionality

### `./universal-build tasks`

* Invokes Gradle's tasks command, showing all available tasks

&nbsp;

## Materialization

Artifacts are named `release-artifact-<owner>--<repository>-<SHA>-<runner>`. OCI image packaging, deployment, and runtime testing remain later materializer stages. A build does not construct or publish an image.

Local outputs remain development feedback. Release artifacts come from the native fleet runner selected for the target platform; image packaging and deployment must use the matching OS/CPU and compatible runtime libraries.

The design choices and future product architecture are documented in the linked [concept pages](#concepts).

## 4. Workflows

### Adding A Java Dependency

1) Find the coordinates on Maven Central (<https://central.sonatype.com>) or the library’s docs.
2) Add the dependency with an explicit version in the right scope:
   * `implementation("group:artifact:version")`
   * `compileOnly("group:artifact:version")`
   * `annotationProcessor("group:artifact:version")`
   * `runtimeOnly("group:artifact:version")`
   * Tests mirror these scopes: `testImplementation`, `testCompileOnly`, `testAnnotationProcessor`, `testRuntimeOnly`
3) Update the Gradle lockfile using the workflow below
4) Run `./universal-build build` to download and verify it resolves; use an IDE “reload Gradle project” if needed.

### Updating Gradle Lockfile

1) Run `./universal-build dependencies --write-locks`

### Adding A Toolchain Dependency

1) Search <https://search.nixos.org/packages> for the package name
2) Add the appropriate package to the package list in the `flake.nix` file

### Updating Nix `nixpkgs` Version

1) Modify the version of `inputs.nixpkgs.url` in the `flake.nix` file to the desired version, according to <https://status.nixos.org>
2) Run `./universal-build --update-nix-flake-lockfile` to update the `flake.lock` lockfile
3) Commit both files to version control

### Ensuring Gradle Files Are Up-To-Date

1) Run `./universal-build wrapper` to update the (this is just a pass-through to `./gradlew wrapper`)
   * This connects to the internet to reset the local self-contained Gradle files to the factory default of the given version. Note that this keeps the same Gradle version.
   * This can help if the Gradle files were stale or modified for some reason (for example, IntelliJ's starting Gradle template gives incorrect files in some cases)

### Upgrading Gradle Version

1) Run `./universal-build wrapper --gradle-version x.y`
   * This connects to the internet to reset the local self-contained Gradle files to the factory default of the given version.

&nbsp;

## 5. Shutdown

Shutdown pattern (implemented in `Main`):

1. SIGTERM (or SIGINT/SIGHUP) fires the shutdown hook, which interrupts the main thread and waits up to `SHUTDOWN_GRACE_PERIOD_SECONDS` seconds for main to finish. Keep that grace period below the platform's SIGTERM-to-SIGKILL timeout (ECS and Kubernetes default to 30s)
2. Main clears the interrupt, performs all resource cleanup in its own `finally` block, then releases the hook
3. The JVM exits after the hook thread returns. On a normal exit the hook returns at once. After a signal the process exits with 128 + the signal number (143 for SIGTERM) regardless of main's exit code

Work must respond to that interrupt:

* The interrupt aborts interruptible waits (sleep, queue and latch waits, `Future.get`, SDK retry backoff) by throwing an exception, which should propagate up to `Main`
* Classic `java.net` socket I/O, `synchronized` and `Lock.lock()` ignore the interrupt, so a call already in flight runs until it returns or times out; normal calls finish well within `SHUTDOWN_GRACE_PERIOD_SECONDS`
* Never swallow `InterruptedException`: declare it, or call `Thread.currentThread().interrupt()` and rethrow it wrapped
* Only `Main` calls `System.exit`; elsewhere throw so the exception reaches `Main`
* CPU-bound loops check `Thread.currentThread().isInterrupted()` between iterations
* Work on other threads is not interrupted; main must stop it in its `finally` block

Correctness is guaranteed by idempotency: dying at any point (including SIGKILL, which bypasses hooks entirely) leaves the system in a consistent state. Graceful shutdown only improves efficiency - it is never required for correctness.

&nbsp;

## 6. Output

The application build produces fleet artifacts; a consuming packaging stage can produce a container image that runs on container runtimes through `Main`. The AWS integrations are examples; the template can also support other cloud providers.

| Runtime | `CMD` |
|---|---|
| Container, ECS task, Kubernetes, EC2 host | `io.template.Main` |

The image's entrypoint applies the Nix runtime environment, then runs `java -XX:MaxRAMPercentage=75.0` on the jars in the distribution's `lib/`, followed by the `CMD`. The JDK and OS userland that run in production are the ones pinned by `flake.lock` and the `Dockerfile`, on every runtime.

&nbsp;

## 7. IDE Setup

### IntelliJ IDEA Ultimate

Follow the instructions here: <https://nader-najjar.notion.site/JetBrains-IDE-Setup-Usage-Guide-d9b0a2b78755822f9d03819f5f02feb2?source=copy_link>
    * Use the command `./universal-build --print-java-path-for-ide` to get the local nix installation of the java version specified in the flake

### Visual Studio Code

Follow the instructions here: <https://nader-najjar.notion.site/Visual-Studio-Code-Setup-Usage-Guide-f4e0a2b7875583be9293817d26459034?source=copy_link>
    * Use the command `./universal-build --print-java-path-for-ide` to get the local nix installation of the java version specified in the flake

&nbsp;

## 8. References

### Nix

* Installation: <https://nix.dev/install-nix>
* Nix Versions: <https://status.nixos.org>
* Nix Package Search: <https://search.nixos.org/packages>

### Maven

* Maven Central Search: <https://central.sonatype.com>

### Jackson

* Jackson Databind Javadoc: <https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/latest/index.html>
* Jackson Databind GitHub README Tutorial: <https://github.com/FasterXML/jackson-databind>
* Jackson Databind Wiki (Including Databind-Specific Annotations): <https://github.com/FasterXML/jackson-databind/wiki>
* Jackson Annotations Javadoc: <https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-annotations/latest/com.fasterxml.jackson.annotation/com/fasterxml/jackson/annotation/package-summary.html>
* Jackson Annotations GitHub README Tutorial: <https://github.com/FasterXML/jackson-annotations>
* Jackson Annotations Wiki: <https://github.com/FasterXML/jackson-annotations/wiki>

### Hibernate

* Main Documentation Page: <https://docs.hibernate.org/validator/9.1/reference/en-US/html_single/>
* Anchor To Available Annotations: <https://docs.hibernate.org/validator/9.1/reference/en-US/html_single/#section-builtin-constraints>

## Concepts

- [Build and OCI Image Packaging](https://app.notion.com/p/3f90a2b787558157a5c2cf7a9b7e92cc?pvs=204)
- [Product Release Pipelines Across Repositories](https://app.notion.com/p/3f90a2b7875581ef953bca201941e0dd?pvs=204)
- [Deployment Testing and Promotion](https://app.notion.com/p/3f90a2b7875581de812ffaddc1e44b83?pvs=204)

## License

All rights reserved. See [LICENSE](LICENSE).
