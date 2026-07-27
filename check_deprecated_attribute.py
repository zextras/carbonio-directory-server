#!/usr/bin/env python3

import json
import re
import subprocess
import sys
from pathlib import Path


ORGANIZATION = "zextras"
ATTRIBUTES_FILE = Path(__file__).with_name("TO_BE_DEPRECATED.md")
ENTRY_PATTERN = re.compile(
    r"^(?P<prefix>\s*-\s+)(?:(?P<marker>⚠️?|✅)\s+)?"
    r"`(?P<attribute>[^`]+)`\s*$"
)
MOCK_PATH_PARTS = {"mock", "mocks", "__mocks__", "api-mocks", "api_mocks"}
MOCK_FILE_PATTERN = re.compile(r"(^|[._-])mocks?([._-]|$)", re.IGNORECASE)


def run_gh(*arguments: str) -> str:
    result = subprocess.run(
        ["gh", *arguments],
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout


def first_unmarked_attribute(lines: list[str]) -> tuple[int, str, str] | None:
    for index, line in enumerate(lines):
        body = line.rstrip("\r\n")
        match = ENTRY_PATTERN.fullmatch(body)
        if match and match.group("marker") is None:
            return index, match.group("prefix"), match.group("attribute")
    return None


def is_mock_path(path: str) -> bool:
    file_path = Path(path)
    return any(part.lower() in MOCK_PATH_PARTS for part in file_path.parts) or bool(
        MOCK_FILE_PATTERN.search(file_path.name)
    )


def is_used_in_active_repository(attribute: str) -> bool:
    output = run_gh(
        "search",
        "code",
        attribute,
        "--owner",
        ORGANIZATION,
        "--limit",
        "1000",
        "--json",
        "repository,path",
    )
    results = json.loads(output)
    repositories = {
        result["repository"]["nameWithOwner"]
        for result in results
        if Path(result["path"]).name != ATTRIBUTES_FILE.name
        and not is_mock_path(result["path"])
    }

    for repository in sorted(repositories):
        details = json.loads(
            run_gh("repo", "view", repository, "--json", "isArchived")
        )
        if not details["isArchived"]:
            return True

    return False


def main() -> int:
    lines = ATTRIBUTES_FILE.read_text(encoding="utf-8").splitlines(keepends=True)
    entry = first_unmarked_attribute(lines)
    if entry is None:
        print("Nessun attributo senza emoji da controllare.")
        return 0

    index, prefix, attribute = entry
    used = is_used_in_active_repository(attribute)
    marker = "⚠️" if used else "✅"
    line_ending = lines[index][len(lines[index].rstrip("\r\n")) :]
    lines[index] = f"{prefix}{marker} `{attribute}`{line_ending}"
    ATTRIBUTES_FILE.write_text("".join(lines), encoding="utf-8")

    status = "utilizzato" if used else "non utilizzato"
    print(f"{marker} {attribute} {status} nei repository attivi di {ORGANIZATION}.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except FileNotFoundError as error:
        print(f"Errore: comando o file non trovato: {error.filename}", file=sys.stderr)
        raise SystemExit(1)
    except subprocess.CalledProcessError as error:
        message = error.stderr.strip() or str(error)
        print(f"Errore durante l'esecuzione di gh: {message}", file=sys.stderr)
        raise SystemExit(1)
