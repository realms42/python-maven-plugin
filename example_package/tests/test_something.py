"""Smoke tests for the plugin's test goal.

These assert very little on purpose. Their job is to prove the lifecycle
works end to end: sources are copied into the work directory, the tools venv
has the project installed, and pytest runs over the result.
"""

import example_package


def test_something():
    assert True


def test_greeting_fixture_is_available(greeting):
    """Fails if conftest.py was not copied alongside the test modules."""
    assert greeting == "Hello from example-package!"


def test_package_is_importable():
    """Fails if the editable install into the tools venv did not happen."""
    assert callable(example_package.main)
