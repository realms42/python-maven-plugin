"""Shared pytest fixtures for the example package.

pytest imports this file automatically, so anything defined here is available
to every test module beside it without an explicit import. It doubles as a
check that the plugin's copy-sources goal brings the whole tests/ tree into
the work directory, not just the test modules themselves.
"""

import pytest


@pytest.fixture
def greeting() -> str:
    """The message example_package.main() prints."""
    return "Hello from example-package!"
