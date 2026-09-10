FROM redhat/ubi10:latest

RUN dnf update -y \
    && dnf install -y maven python3 python3-pip \
    && python3 -m pip install --no-cache-dir build twine \
    && dnf clean all \
    && rm -rf /var/cache/dnf

WORKDIR /plugin
COPY python-maven-plugin/pom.xml .
RUN mvn dependency:go-offline -B
COPY python-maven-plugin .
RUN mvn install -B

WORKDIR /example
COPY example_package .
# RUN --mount=type=cache,target=/root/.m2 mvn install -B
