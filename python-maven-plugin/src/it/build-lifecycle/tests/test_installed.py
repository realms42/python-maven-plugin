import importlib.metadata
import re

import it_pkg


def test_runs_against_the_installed_wheel():
    assert "site-packages" in it_pkg.__file__
    assert re.fullmatch(r"1\.0\.0\.dev\d{14}", importlib.metadata.version("it_pkg"))
