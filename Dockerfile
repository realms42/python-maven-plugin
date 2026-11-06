FROM redhat/ubi10:latest

RUN dnf update -y \
    && dnf install -y maven python3 \
    && dnf clean all \
    && rm -rf /var/cache/dnf

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/
# A mounted uv cache (the cache mount below, or a docker run volume) is on another filesystem, so uv
# copies from it instead of hardlinking; setting that explicitly stops uv warning about it on every run.
ENV UV_LINK_MODE=copy

WORKDIR /plugin
COPY python-maven-plugin/pom.xml .
RUN mvn dependency:go-offline -B
COPY python-maven-plugin .
# The image only builds: tests, integration tests and checks run in CI, not here.
RUN mvn install -B -Dmaven.test.skip=true -Dinvoker.skip

WORKDIR /example
COPY example_package .
# No cache mount on /root/.m2: it would start empty and hide the plugin installed there above.
# Builds and packages the example without its tests, lint or format check.
RUN --mount=type=cache,target=/root/.cache/uv \
    mvn install -B -Dmaven.test.skip=true -Dpython.lint.skip -Dpython.format.skip
