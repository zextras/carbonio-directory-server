#!/usr/bin/env python3

import json
import re
import subprocess
import sys
import time
from pathlib import Path


ORGANIZATION = "zextras"
ATTRIBUTES_FILE = Path(__file__).with_name("TO_BE_DEPRECATED.md")
CHECK_INTERVAL_SECONDS = 10
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


def unmarked_attributes(lines: list[str]) -> list[tuple[int, str, str]]:
    entries = []
    for index, line in enumerate(lines):
        body = line.rstrip("\r\n")
        match = ENTRY_PATTERN.fullmatch(body)
        if match and match.group("marker") is None:
            entries.append((index, match.group("prefix"), match.group("attribute")))
    return entries


def is_rate_limit_error(error: subprocess.CalledProcessError) -> bool:
    message = "\n".join(
        part for part in (error.stderr, error.stdout, str(error)) if part
    )
    return bool(re.search(r"\brate[\s_-]?limit(?:ed|er|ing)?\b", message, re.IGNORECASE))


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
    entries = unmarked_attributes(lines)
    if not entries:
        print("Nessun attributo senza emoji da controllare.")
        return 0

    checked = 0
    for position, (index, prefix, attribute) in enumerate(entries):
        if position > 0:
            time.sleep(CHECK_INTERVAL_SECONDS)

        try:
            used = is_used_in_active_repository(attribute)
        except subprocess.CalledProcessError as error:
            message = (error.stderr or "").strip() or str(error)
            if is_rate_limit_error(error):
                print(
                    f"Rate limit GitHub raggiunto durante il controllo di {attribute}: "
                    f"{message}",
                    file=sys.stderr,
                )
                return 2

            print(
                f"Errore di gh durante il controllo di {attribute}: {message}",
                file=sys.stderr,
            )
            return 1

        marker = "⚠️" if used else "✅"
        line_ending = lines[index][len(lines[index].rstrip("\r\n")) :]
        lines[index] = f"{prefix}{marker} `{attribute}`{line_ending}"
        ATTRIBUTES_FILE.write_text("".join(lines), encoding="utf-8")

        checked += 1
        status = "utilizzato" if used else "non utilizzato"
        print(f"{marker} {attribute} {status} nei repository attivi di {ORGANIZATION}.")

    print(f"Controllo completato: {checked} attributi controllati.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except FileNotFoundError as error:
        print(f"Errore: comando o file non trovato: {error.filename}", file=sys.stderr)
        raise SystemExit(1)
