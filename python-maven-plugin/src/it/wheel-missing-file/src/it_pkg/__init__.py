from importlib.resources import files


def greet(name: str) -> str:
    template = files(__package__).joinpath("greeting.txt").read_text()
    return template.strip().format(name=name)
