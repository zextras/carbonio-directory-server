FROM --platform=$BUILDPLATFORM maven:3-eclipse-temurin-21-alpine AS build
RUN apk add --no-cache git
WORKDIR /build

COPY .git                             .git

COPY pom.xml pom.xml
COPY src src

RUN --mount=type=cache,target=/root/.m2 mvn clean install -DskipTests

FROM --platform=$BUILDPLATFORM ubuntu:jammy AS openldap-installer

ARG TARGETARCH
ARG BUILDARCH=amd64

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates openssl \
 # Cross-arch setup when TARGETARCH != BUILDARCH
 && if [ "${TARGETARCH}" != "${BUILDARCH}" ]; then \
      dpkg --add-architecture ${TARGETARCH} \
      && sed -i 's|^deb |deb [arch='"${BUILDARCH}"'] |' /etc/apt/sources.list \
      && printf 'Types: deb\nURIs: http://ports.ubuntu.com/ubuntu-ports/\nSuites: jammy jammy-updates jammy-security\nComponents: main restricted universe multiverse\nArchitectures: %s\nSigned-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\n' "${TARGETARCH}" \
         > /etc/apt/sources.list.d/ports-${TARGETARCH}.sources; \
    fi \
 # Add zextras devel repo (area51 public mirror of JFrog ubuntu-devel)
 && printf 'deb [arch=%s trusted=yes] https://repo.area51-zextras.com/devel/ubuntu jammy main\n' "${TARGETARCH}" \
    > /etc/apt/sources.list.d/zextras.list \
 && apt-get update \
 # Download only the packages needed to run slapd — no dep resolution, no carbonio-core
 && mkdir -p /staging /tmp/debs && cd /tmp/debs \
 && apt-get download \
      carbonio-openldap:${TARGETARCH} \
      carbonio-openssl:${TARGETARCH} \
      carbonio-cyrus-sasl:${TARGETARCH} \
      carbonio-libsodium:${TARGETARCH} \
      libcap2-bin:${TARGETARCH} \
      libltdl7:${TARGETARCH} \
      libsystemd0:${TARGETARCH} \
      netcat-openbsd:${TARGETARCH} \
      libbsd0:${TARGETARCH} \
      libmd0:${TARGETARCH} \
      openssl:${TARGETARCH} \
 # Extract all debs with dpkg -x — no postinst scripts run
 && for deb in /tmp/debs/*.deb; do dpkg -x "$deb" /staging; done \
 && rm -rf /tmp/debs \
 # Collect arch-specific libs into /staging/usr/local/lib for arch-independent COPY
 && mkdir -p /staging/usr/local/lib \
 && find /staging/lib /staging/usr/lib -name '*.so*' -not -path '*/opt/*' \
    -exec cp -P {} /staging/usr/local/lib/ \; 2>/dev/null || true \
 # Fix netcat: binary may land in bin/ or usr/bin/ depending on arch — normalise to bin/
 && find /staging/bin /staging/usr/bin -name 'nc.openbsd' -exec cp {} /staging/bin/nc.openbsd \; 2>/dev/null || true \
 && ln -sf nc.openbsd /staging/bin/netcat \
 && ln -sf nc.openbsd /staging/bin/nc \
 # Pre-create zextras user/group entries for the final stage (passwd/group only)
 && groupadd -r zextras \
 && useradd -r -g zextras -d /opt/zextras -s /bin/bash zextras \
 && mkdir -p /staging/etc \
 && cp /etc/passwd /etc/group /staging/etc/ \
 && rm -rf /var/lib/apt/lists/*

FROM --platform=$TARGETPLATFORM ubuntu:jammy

# Copy extracted openldap tree — no RUN needed, no QEMU required
COPY --from=openldap-installer /staging/opt /opt
COPY --from=openldap-installer /staging/usr/local/lib /usr/local/lib/
COPY --from=openldap-installer /staging/usr/bin/openssl /usr/bin/openssl
COPY --from=openldap-installer /staging/bin /bin/
COPY --from=openldap-installer /staging/sbin /sbin/
COPY --from=openldap-installer /staging/etc/passwd /etc/passwd
COPY --from=openldap-installer /staging/etc/group /etc/group
ENV LD_LIBRARY_PATH=/usr/local/lib
ENV PATH=/opt/zextras/common/bin:/opt/zextras/common/sbin:$PATH
ENV LDAP_ROOT_PASSWORD=qh6hWZvc
ENV LDAP_ADMIN_PASSWORD=password
EXPOSE 1389

COPY docker/openldap/ldap-utils/ /ldap-utils/
COPY --from=build /build/src/ldap/generated/ /opt/zextras/common/etc/openldap/zimbra/

COPY docker/openldap/entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
