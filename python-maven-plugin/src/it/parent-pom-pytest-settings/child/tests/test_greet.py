import pytest

from it_pkg import greet


@pytest.mark.xfail(reason="marked as failing, but passes")
def test_greet():
    assert greet("Maven") == "Hello, Maven!"
