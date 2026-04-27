# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Keep README.md in sync

When a change alters user-facing build commands, package targets, directory layout, attribute-editing rules, CI behavior, or release flow, update `README.md` in the same change. Treat the README as the public-facing counterpart of this file — anything documented here that a contributor would also need to know belongs there too.

## What this repo is

Two deliverables live together in this repo:

1. **Maven artifact `com.zextras:mailbox-attribute-manager`** — a Java 21 library that parses Zimbra/Carbonio attribute definitions (`src/main/resources/conf/attrs/*.xml`) and exposes `AttributeManager` to the mailbox server. Published to Zextras Artifactory.
2. **OS package `carbonio-directory-server`** — a deb/rpm that ships the generated OpenLDAP schema, LDIF seeds, external directory sync templates, and systemd target for Carbonio's OpenLDAP instance. Packaged via YAP from `directory-server/PKGBUILD`.

The same Maven build produces both: running the package phase triggers `AttributeManagerUtil` to generate LDAP schema + LDIFs into `src/ldap/generated/`, which the PKGBUILD then installs under `/opt/zextras/common/etc/openldap/zimbra/`.

## Common commands

```bash
mvn clean install              # full build (compiles, generates LDAP artifacts, runs tests)
mvn clean install -DskipTests  # what CI uses for the build stage
mvn verify                     # run tests + JaCoCo report + failsafe
mvn test -Dtest=AttributeManagerTest                       # single test class
mvn test -Dtest=AttributeManagerTest#someMethodName        # single test method
mvn -Pprod ...                 # release profile — strips the -SNAPSHOT changelist

./build_packages.sh ubuntu-jammy   # build deb/rpm via YAP docker image (also: rocky-8, rocky-9, ubuntu-noble)
docker build -f docker/openldap/Dockerfile -t carbonio-openldap .   # build the OpenLDAP container
```

Maven enforces strict dependency hygiene — `maven-dependency-plugin` fails the build on undeclared deps, and `maven-enforcer-plugin` bans duplicate POM versions and requires dependency convergence. Add new dependencies to `<dependencyManagement>` with an explicit version before declaring them in `<dependencies>`.

## Architecture

### Attribute definition pipeline

`attrs.xml` (plus `amavisd-new-attrs.xml`, `ocs.xml`) is the single source of truth for every Carbonio/Zimbra LDAP attribute and objectclass. The build turns it into multiple artifacts:

- `AttributeManager` (`src/main/java/com/zimbra/cs/account/AttributeManager.java`) loads the XML at runtime via `AttributeStream` / `ResourceAttributeStream` and is consumed by mailbox server code via the Maven artifact.
- `AttributeManagerUtil` is a CLI driver invoked from `pom.xml`'s `exec-maven-plugin` during `prepare-package` to generate:
  - `schema/carbonio.schema` + `schema/carbonio.ldif` (from `carbonio.schema-template`)
  - `zimbra_globalconfig.ldif`
  - `zimbra_defaultcos.ldif` / `zimbra_defaultexternalcos.ldif`
  - `generateAttrDocs` → `target/attr-docs/` (static HTML/JS/CSS + `attrs.json` + `version.json`) — the searchable admin docs bundle. Also zipped to `target/carbonio-attrs-docs-<version>.zip` by an antrun step and archived by Jenkins (`Archive attribute docs` stage). Implemented in `com.zimbra.cs.account.AttrDocsGenerator`; UI assets live in `src/main/resources/docs/`.
- `RightDomainAdminResourceGenerator` runs in `process-test-classes` to generate `conf/rights/rights-domainadmin.xml` from the `.xml-template` by intersecting modifiable attrs across `account`/`calendarResource`/`distributionList`/`domain` classes.
- `LdifProvider` (`src/main/java/com/zextras/ldap/LdifProvider.java`) is how downstream Java code reads the generated LDIFs out of the packaged jar resources.

Everything generated lands in `src/ldap/generated/` (gitignored) alongside hand-maintained files from `src/ldap/` (`carbonio.ldif`, `mimehandlers.ldif`, `amavisd.schema`, `opendkim.ldif`, and the `config/cn=config/...` tree). The `maven-antrun-plugin` copy step in `pom.xml` orchestrates this layout.

### Rules for modifying `attrs.xml`

Read the comment block at the top of `src/main/resources/conf/attrs/attrs.xml` before touching it. Summary:
- Always append; never reorder. OIDs are positional.
- Never change or reuse an id after release.
- New attrs must start with `zimbra` or `carbonio`. User-pref attrs must be prefixed `zimbraPref*` / `carbonioPrefs*` so `ModifyPrefs` SOAP picks them up.
- The `prepare-package` step regenerates `attrs-schema` from `git log -1 --pretty=format:%at` on `attrs.xml`, so committing changes to that file bumps the schema version exposed to the server.

### LDAP upgrades

Runtime upgrade scripts live in `src/ldap/src/` (`LdapPreFlight.pl`, `LdapMigrationUtils.pm`) and `src/ldap/src/migrations/pre_flight/`. Incremental attribute changes for already-installed instances are declared as timestamped JSON files under `src/ldap/src/updates/attrs/<unix-timestamp>.json` — the timestamps define application order and must be unique. `PKGBUILD`'s `_ldap_upgrade_*` functions run `LdapPreFlight.pl`, `zmldapupdateldif`, and `ldapattributeupdate` on package upgrade.

### Packaging targets

`PKGBUILD` dispatches per-distro via `package__<distro>` / `postinst__<distro>` / `preinst__<distro>` / `prerm__<distro>` functions. Rocky 8 + Ubuntu Jammy use the legacy `zmcontrol`-style ldap start/stop; Rocky 9 + Ubuntu Noble use `systemctl` against `carbonio-openldap.service` + the `carbonio-directory-server.target`. `_ldap_fix_setcap` applies `CAP_NET_BIND_SERVICE` to `slapd` after each upgrade because `chmod` clears file capabilities.

## Release process

`release.config.mjs` drives semantic-release on pushes to `main`. It bumps `<revision>` in `pom.xml` and `pkgver` in `directory-server/PKGBUILD` from conventional-commit history (`feat` → minor, `fix`/`refactor`/`build`/`ci`/`perf` → patch). Don't hand-edit the version in those two files — write a conventional commit instead. `chore(release): ...` commits are created by the bot and should not be squashed.

Tag builds (`isBuildingTag()` in `Jenkinsfile`) activate the `-Pprod` profile, which removes the `-SNAPSHOT` changelist.