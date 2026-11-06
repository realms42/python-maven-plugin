FROM redhat/ubi10:latest

RUN dnf update -y \
    && dnf install -y maven python3 \
    && dnf clean all \
    && rm -rf /var/cache/dnf

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/
ENV UV_LINK_MODE=copy

WORKDIR /plugin
COPY python-maven-plugin/pom.xml .
RUN mvn dependency:go-offline -B
COPY python-maven-plugin .
RUN mvn install -B -Dmaven.test.skip=true -Dinvoker.skip

WORKDIR /examples
COPY example_parent example_parent
COPY example_package example_package
WORKDIR /examples/example_parent
RUN --mount=type=cache,target=/root/.cache/uv \
    mvn install -B -Dmaven.test.skip=true -Dpython.lint.skip -Dpython.format.skip
