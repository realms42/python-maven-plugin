import pytest
from example_package import __version__

@pytest.fixture
def version() -> str:
    return __version__
