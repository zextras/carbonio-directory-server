FROM maven:3-eclipse-temurin-17-alpine AS build
RUN apk add --no-cache git
WORKDIR /build

COPY .git                             .git

COPY pom.xml pom.xml
COPY src src

RUN --mount=type=cache,target=/root/.m2 mvn clean install -DskipTests

FROM ubuntu:jammy

USER root
RUN apt update && apt install -y gnupg2 ca-certificates && apt clean \
&& apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 52FD40243E584A21 \
&& echo deb https://repo.zextras.io/release/ubuntu jammy main > /etc/apt/sources.list.d/zextras.list \
&& apt update && apt install -y carbonio-openldap netcat && apt clean

ENV LDAP_ROOT_PASSWORD=qh6hWZvc
ENV LDAP_ADMIN_PASSWORD=password
EXPOSE 1389

COPY docker/openldap/ldap-utils/ /ldap-utils/
COPY --from=build /build/src/ldap/generated/ /opt/zextras/common/etc/openldap/zimbra/

COPY docker/openldap/entrypoint.sh .
ENTRYPOINT ["./entrypoint.sh"]