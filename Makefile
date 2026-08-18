.PHONY: build test release verify-release

GRADLE ?= ./gradlew
PYTHON ?= python3

build:
	$(GRADLE) :app:assembleDebug

test:
	$(GRADLE) :app:testDebugUnitTest

release:
	@PYTHON="$(PYTHON)" V="$(V)" bash scripts/release.sh

verify-release:
	@test -n "$(TAG)" || (echo "TAG is required, e.g. make verify-release TAG=v1.0.0" >&2; exit 2)
	@$(PYTHON) scripts/repo_version.py --root . check "$(TAG)"
