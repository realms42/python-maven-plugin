"""Reads a wheel's real file name and version from its metadata.

Maven stores the wheel as example_package-py3-none-any.whl, which pip won't install.
"""

import re
import sys
import zipfile


def info(path):
    with zipfile.ZipFile(path) as wheel:
        dist_info = next(n.split("/")[0] for n in wheel.namelist() if n.endswith(".dist-info/WHEEL"))
        lines = wheel.read(f"{dist_info}/WHEEL").decode().splitlines()
    tag = next(line.split(":", 1)[1].strip() for line in lines if line.startswith("Tag:"))
    name, version = dist_info.removesuffix(".dist-info").split("-", 1)
    return name, version, tag


def main(what, path):
    name, version, tag = info(path)
    if what == "filename":
        print(f"{name}-{version}-{tag}.whl")
    elif what == "rpm-version":
        # Pre-releases sort before the release in RPM only with ~, as Fedora's guidelines do.
        print(re.sub(r"\.?(dev|a|b|rc)(\d+)", r"~\1\2", version))
    else:
        sys.exit(f"unknown: {what}")


if __name__ == "__main__":
    main(*sys.argv[1:])
