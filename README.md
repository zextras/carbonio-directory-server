# Carbonio Directory Server

This repository produces two related deliverables from a single Maven build:

- **`mailbox-attribute-manager`** — a Java 21 library (`com.zextras:mailbox-attribute-manager`) that parses the Carbonio attribute definitions in `src/main/resources/conf/attrs/` and exposes `AttributeManager` to the mailbox server. Published to Zextras Artifactory.
- **`carbonio-directory-server`** — a deb/rpm package that ships the generated OpenLDAP schema, LDIF seeds, external directory sync templates, and the systemd target for Carbonio's OpenLDAP instance.

The Maven build invokes `AttributeManagerUtil` during `prepare-package` to generate the LDAP schema and LDIFs into `src/ldap/generated/`, which the `directory-server/PKGBUILD` then installs under `/opt/zextras/common/etc/openldap/zimbra/`.

## Requirements

- JDK 21
- Maven 3.9+
- Docker (only for building deb/rpm packages via YAP)

## Building

```bash
mvn clean install                 # compile, generate LDAP artifacts, run tests
mvn clean install -DskipTests     # skip tests (what CI uses for the build stage)
mvn verify                        # run tests + failsafe + JaCoCo report
TAG_NAME=v1.2.3 mvn clean install # release build (blanks the -SNAPSHOT changelist)
```

Run a single test:

```bash
mvn test -Dtest=AttributeManagerTest
mvn test -Dtest=AttributeManagerTest#someMethodName
```

## Packages

Build deb/rpm packages via the YAP docker image:

```bash
./build_packages.sh ubuntu-jammy   # also: rocky-8, rocky-9, ubuntu-noble
```

Artifacts are written to `artifacts/<os>/`.

## Attribute docs bundle

The build also produces a self-contained, searchable static docs site for every attribute and objectclass defined in `src/main/resources/conf/attrs/*.xml`. Output lands in `target/attr-docs/` (unzipped) and `target/carbonio-attrs-docs-<version>.zip`:

```
target/attr-docs/
  index.html     search UI (single page, vanilla JS)
  app.js
  style.css
  attrs.js       ~1.1 MB — window.ATTRS_DATA with all attrs + objectclasses
  version.js     window.VERSION_DATA = { version, commit, generatedAt }
```

Data is shipped as JS globals loaded via `<script>` tags (not `fetch()`) so the bundle works both from `file://` (local preview) and over HTTP. Open `target/attr-docs/index.html` directly in a browser to preview. Deprecated attributes are hidden by default — tick the "Show deprecated" filter in the sidebar to include them. The zip is archived by Jenkins on every successful build and is intended for the docs team to upload to the Carbonio documentation site per release. Regenerate on demand with:

```bash
mvn -DskipTests=true prepare-package
```

## Container image

The `carbonio-openldap` container is built from `docker/openldap/Dockerfile`:

```bash
docker build -f docker/openldap/Dockerfile -t carbonio-openldap .
```

## Layout

- `src/main/java/com/zimbra/cs/account/` — `AttributeManager`, `AttributeManagerUtil`, attribute model classes, code generators.
- `src/main/java/com/zextras/ldap/LdifProvider.java` — runtime accessor for the generated LDIFs shipped inside the jar.
- `src/main/resources/conf/attrs/` — attribute definition XML (`attrs.xml`, `amavisd-new-attrs.xml`, `ocs.xml`).
- `src/main/resources/conf/rights/` — right definitions and the domain-admin rights template.
- `src/ldap/` — hand-maintained LDAP assets (`carbonio.ldif`, `carbonio.schema-template`, `cn=config` tree, etc.).
- `src/ldap/src/updates/attrs/` — timestamped JSON files declaring per-release attribute additions applied on upgrade.
- `src/ldap/src/migrations/pre_flight/` — pre-upgrade migration scripts.
- `directory-server/` — `PKGBUILD`, systemd target, and tmpfile config for the OS package.
- `docker/openldap/` — `Dockerfile` and entrypoint for the `carbonio-openldap` container image.

## Modifying attributes

Read the comment block at the top of `src/main/resources/conf/attrs/attrs.xml` before touching it. In brief:

- Always append new attributes; never reorder. OIDs are positional.
- Never change or reuse an `id` after release.
- New attribute names must start with `zimbra*` or `carbonio*`; user-pref attributes must be prefixed `zimbraPref*` / `carbonioPrefs*` so the `ModifyPrefs` SOAP API picks them up.

Incremental attribute changes for already-installed instances are declared as timestamped JSON files in `src/ldap/src/updates/attrs/<unix-timestamp>.json`. The timestamps define application order and must be unique.

## CI

`Jenkinsfile` defines the pipeline, which runs on the `zextras-v1` agent and delegates almost everything to shared steps from `jenkins-lib-common`:

| Stage | Step | Notes |
| --- | --- | --- |
| Setup | `checkout scm` + `gitMetadata()` | |
| Skip CI | `semanticRelease.guard()` | aborts builds of the release bot's own version-bump commits |
| Security Scan | `gitleaksStage()` | secret scan |
| Maven | `mavenStage()` | expands into Maven Build / Maven Test / SonarQube Analysis / Maven Deploy on `jdk-21`. Sonar runs on `main` and PRs; deploy runs on `main` and tags |
| Docker images | `dockerStage()` | publishes `carbonio-openldap` for `linux/amd64` + `linux/arm64` |
| Build deb/rpm | `buildStage()` | YAP packages |
| Archive attribute docs | `archiveArtifacts` | `target/carbonio-attrs-docs-*.zip` |
| Upload artifacts | `uploadStage()` | |
| Bump version | `semanticRelease()` | |

## Releases

`release.config.mjs` drives semantic-release on pushes to `main`. Conventional-commit history determines the version bump (`feat` → minor; `fix` / `refactor` / `build` / `ci` / `perf` → patch), and the bot updates `<revision>` in `pom.xml` and `pkgver` in `directory-server/PKGBUILD` — do not edit those versions by hand.

## License

See `COPYING`. Source files carry per-file SPDX headers (`GPL-2.0-only` or `AGPL-3.0-only`).
