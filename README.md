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
mvn -Pprod clean install          # release profile (strips the -SNAPSHOT changelist)
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

`Jenkinsfile` defines the pipeline, which runs on the `zextras-v1` agent inside a `jdk-21` container. It builds with `mvn clean install -DskipTests`, then runs `mvn verify` and publishes JUnit reports, followed by a SonarQube analysis. On `devel` and tags, it publishes the `carbonio-openldap` container image to `registry.dev.zextras.com` (tagged `devel`/`latest` for `devel`, the tag name plus `stable` for tags). Maven artifacts are deployed to Zextras Artifactory as SNAPSHOTs on non-tag builds and as releases on tags (with the `-Pprod` profile). The pipeline also builds the deb/rpm packages, uploads them, and finally runs `dt2_semanticRelease` to bump the version. A nightly cron (`H 5 * * *`) triggers the pipeline on `devel`.

## Releases

`release.config.mjs` drives semantic-release on pushes to `main`. Conventional-commit history determines the version bump (`feat` → minor; `fix` / `refactor` / `build` / `ci` / `perf` → patch), and the bot updates `<revision>` in `pom.xml` and `pkgver` in `directory-server/PKGBUILD` — do not edit those versions by hand.

## License

See `COPYING`. Source files carry per-file SPDX headers (`GPL-2.0-only` or `AGPL-3.0-only`).
