# Build and publication verification

Use JDK 17+ and Gradle 9.7.1. The SDK has no external runtime dependencies; JUnit is a test dependency. The optional verification init script loads SpotBugs Gradle plugin 6.5.11 / engine 4.10.4 only at build time. It makes all javac warnings fatal and checks resolved runtime dependencies are empty.

```sh
gradle --no-daemon -I tools/verification.init.gradle clean test spotbugsMain resolvedAuditDependencies centralBundle -PlunixiUnsigned
python3 tools/check-artifacts.py
python3 tools/audit-dependencies.py
python3 tools/check-secrets.py
```

The artifact checker inspects actual class-file major version 61 (JDK 17), library/source/Javadoc JAR boundaries, public paths, the Maven POM and the unsigned staging bundle. Unit/contract tests run on JDK 17 and 21 in CI. Local acceptance additionally executes fresh JVMs against disk reservations to prove stable body/key bytes. External gateway transport is synthetic; no live merchant charge or storefront certification is claimed.

SpotBugs medium/high-confidence findings fail the build. Its exact method filters document the intentionally shared TokenStore SPI and read-only defensive-copy getter false positives. Nested-map/list, input-alias and public mutation regressions prove those getter contracts; no category, package or security detector is globally excluded.

`resolvedAuditDependencies` writes exact resolved Maven test coordinates. `audit-dependencies.py` sends only those coordinates to the public [OSV batch API](https://google.github.io/osv.dev/post-v1-querybatch/), follows pagination and fails if results are unavailable/incomplete or advisories exist. It saves a report under `build/security/`. This is a current advisory lookup, not a guarantee against unknown vulnerabilities. Build-time scanner/plugin dependencies and the JDK/Gradle distribution have their own release/security policies.

Install [Gitleaks](https://github.com/gitleaks/gitleaks) 8.30.1 for `check-secrets.py`. The source scanner exports all source/docs/CI with only generated build/cache/VCS directories excluded, retains default credential rules, and allows only exact reviewed synthetic fixture matches. Its negative control inserts an additional synthetic credential into an allowlisted test file and requires detection, proving the whole file is not exempted. Reports are redacted; no real credential is used.

GitHub Actions are pinned by commit SHA and use read-only repository permissions. CI builds and checks an **unsigned** bundle without registry credentials; it never publishes Maven Central. A source tag, `publishToMavenLocal`, and a signed Central upload are separate operations. The POM coordinate is `com.lunixi:lunixi-java-sdk:1.0.1`. Central requires a separately authorized signed release; unsigned layout acceptance must never be presented as registry availability.

Primary tooling references: [Gradle JVM compatibility](https://docs.gradle.org/current/userguide/compatibility.html), [SpotBugs plugin 6.5.11](https://github.com/spotbugs/spotbugs-gradle-plugin/releases/tag/6.5.11), [OSV API](https://google.github.io/osv.dev/api/), [Gitleaks configuration](https://github.com/gitleaks/gitleaks#configuration).


## 0.2.1 checkout contract regression receipt

**Historical verification receipt:** the following counts and tool results refer to 0.2.1.

Targeted PaymentsTest + DurableCommerceServiceTest: 22 tests passed, zero failures/skips. Strict -Xlint:all/-Werror compilation, SpotBugs (zero findings) and JAR/source/Javadoc packaging passed. Includes exact backend URL retention, malformed/origin-lookalike/dot-path rejection, legacy embedded-only response and encrypted durable URL cache/reopen without key/body rotation. Runtime dependency set remains empty. This focused patch validation does not claim a new full historical suite or live merchant charge.
