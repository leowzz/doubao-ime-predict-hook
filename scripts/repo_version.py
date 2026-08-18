#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from collections.abc import Sequence
from pathlib import Path


TAG_VERSION_RE = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
VERSION_NAME_RE = re.compile(r"^VERSION_NAME=([^\s#]+)\s*$")
VERSION_CODE_RE = re.compile(r"^VERSION_CODE=(\d+)\s*$")
VERSION_PROPERTIES = "gradle.properties"


class VersionError(ValueError):
    pass


def validate_tag_version(value: str) -> str:
    if TAG_VERSION_RE.fullmatch(value) is None:
        raise VersionError(f"expected vX.Y.Z, got {value!r}")
    return value


def numeric_version(tag: str) -> str:
    return validate_tag_version(tag)[1:]


def bump_patch(tag: str) -> str:
    match = TAG_VERSION_RE.fullmatch(validate_tag_version(tag))
    assert match is not None
    major, minor, patch = (int(part) for part in match.groups())
    return f"v{major}.{minor}.{patch + 1}"


def version_code(tag: str) -> int:
    major, minor, patch = (int(part) for part in numeric_version(tag).split("."))
    code = major * 1_000_000 + minor * 1_000 + patch
    if not 1 <= code <= 2_147_483_647:
        raise VersionError(f"version code is out of Android range for {tag}")
    return code


def _property_lines(path: Path) -> tuple[list[str], int, int]:
    try:
        lines = path.read_text().splitlines(keepends=True)
    except FileNotFoundError as error:
        raise VersionError(f"missing {path}") from error

    version_matches = [
        index
        for index, line in enumerate(lines)
        if VERSION_NAME_RE.fullmatch(line.rstrip("\n\r"))
    ]
    code_matches = [
        index
        for index, line in enumerate(lines)
        if VERSION_CODE_RE.fullmatch(line.rstrip("\n\r"))
    ]
    if len(version_matches) != 1:
        raise VersionError(f"{path}: expected exactly one VERSION_NAME field")
    if len(code_matches) != 1:
        raise VersionError(f"{path}: expected exactly one VERSION_CODE field")
    return lines, version_matches[0], code_matches[0]


def read_repo_version(root: Path) -> tuple[str, int]:
    path = root / VERSION_PROPERTIES
    lines, version_index, code_index = _property_lines(path)
    version_match = VERSION_NAME_RE.fullmatch(lines[version_index].rstrip("\n\r"))
    code_match = VERSION_CODE_RE.fullmatch(lines[code_index].rstrip("\n\r"))
    assert version_match is not None
    assert code_match is not None
    version = version_match.group(1)
    if re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", version) is None:
        raise VersionError(f"{path}: VERSION_NAME must be X.Y.Z, got {version!r}")
    code = int(code_match.group(1))
    if code < 1:
        raise VersionError(f"{path}: VERSION_CODE must be positive")
    return version, code


def set_repo_version(root: Path, tag: str) -> None:
    tag = validate_tag_version(tag)
    path = root / VERSION_PROPERTIES
    lines, version_index, code_index = _property_lines(path)
    read_repo_version(root)
    lines[version_index] = f"VERSION_NAME={numeric_version(tag)}\n"
    lines[code_index] = f"VERSION_CODE={version_code(tag)}\n"
    path.write_text("".join(lines))


def check_repo_version(root: Path, expected_tag: str | None = None) -> str:
    version, code = read_repo_version(root)
    current_tag = validate_tag_version(f"v{version}")
    expected_tag = current_tag if expected_tag is None else validate_tag_version(expected_tag)
    if current_tag != expected_tag:
        raise VersionError(
            f"repository version {expected_tag} mismatch: VERSION_NAME={version}"
        )
    if code != version_code(expected_tag):
        raise VersionError(
            f"repository version {expected_tag} mismatch: VERSION_CODE={code}"
        )
    return expected_tag


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    commands = parser.add_subparsers(dest="command", required=True)
    validate = commands.add_parser("validate")
    validate.add_argument("tag")
    get = commands.add_parser("get")
    get.add_argument("--bump-patch", action="store_true")
    set_command = commands.add_parser("set")
    set_command.add_argument("tag")
    check = commands.add_parser("check")
    check.add_argument("tag", nargs="?")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "validate":
            print(validate_tag_version(args.tag))
        elif args.command == "get":
            version, _ = read_repo_version(args.root)
            tag = f"v{version}"
            print(bump_patch(tag) if args.bump_patch else tag)
        elif args.command == "set":
            set_repo_version(args.root, args.tag)
        elif args.command == "check":
            print(check_repo_version(args.root, args.tag))
    except VersionError as error:
        print(error, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
