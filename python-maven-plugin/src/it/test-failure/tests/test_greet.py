from it_pkg import greet


def test_greet():
    assert greet("Maven") == "Goodbye"
