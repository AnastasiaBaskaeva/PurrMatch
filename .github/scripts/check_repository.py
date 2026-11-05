import argparse
from functools import lru_cache
import json
import os
from pathlib import Path
import re
import subprocess
import sys


AREAS = ("android", "backend", "infra")
COMMIT = re.compile(r"(?:[0-9a-f]{40}|[0-9a-f]{64})\Z")
MARKDOWN = (".md", ".markdown", ".mdown")
PUBLISHED_ANDROID_DOCS = {
    "android/README.md", "android/docs/api-contract.md", "android/presentation/README.md",
}


class PolicyError(Exception):
    pass


class Repository:
    def __init__(self, path):
        self.path = str(path)

    def git(self, *args):
        result = subprocess.run(
            ["git", "-C", self.path, *args], capture_output=True, check=False
        )
        if result.returncode:
            message = result.stderr.decode("utf-8", errors="replace").strip()
            raise PolicyError(message or "Git validation failed: " + " ".join(args))
        return result.stdout

    def commit(self, revision):
        value = self.git("rev-parse", "--verify", revision + "^{commit}").decode().strip()
        if not COMMIT.fullmatch(value):
            raise PolicyError("Invalid commit revision")
        return value

    def ancestor(self, ancestor, descendant):
        self.git("merge-base", "--is-ancestor", ancestor, descendant)

    @lru_cache(maxsize=None)
    def tree(self, revision):
        entries = {}
        for entry in self.git("ls-tree", "-r", "-z", "--full-tree", revision).split(b"\0"):
            if entry:
                metadata, path = entry.split(b"\t", 1)
                entries[path.decode("utf-8", errors="surrogateescape")] = tuple(metadata.split())
        return entries

    def changes(self, base, head):
        merge_base = self.git("merge-base", base, head).decode().strip()
        paths = self.git(
            "diff", "--no-ext-diff", "--no-textconv", "--no-renames", "--name-only",
            "-z", merge_base, head, "--",
        )
        return merge_base, [
            path.decode("utf-8", errors="surrogateescape")
            for path in paths.split(b"\0") if path
        ]


def branch_area(base, head):
    if not isinstance(base, str) or not isinstance(head, str):
        raise PolicyError("Branch names must be strings")
    if base == "main":
        for area in AREAS:
            if head == area + "/trunk":
                return area
    for area in AREAS:
        if base == area + "/trunk" and head.startswith(area + "/") and head != area + "/":
            return area
    raise PolicyError("Expected feature → matching trunk, or matching trunk → main")


def owns(area, path):
    if path.startswith(area + "/") or path.startswith("contracts/"):
        return True
    if area == "android" and path == ".github/workflows/android.yml":
        return True
    return area == "infra" and (
        path.startswith(".github/")
        or path in {".gitignore", ".dockerignore", "backend/Dockerfile", "backend/.dockerignore"}
    )


def sync_revision(repo, area, base, head, head_sha):
    prefix = area + "/sync-main-"
    if not head.startswith(prefix):
        return None
    suffix = head[len(prefix):]
    if base != area + "/trunk" or not re.fullmatch(r"[0-9a-f]{7,40}|[0-9a-f]{64}", suffix):
        raise PolicyError("A sync branch must identify the main commit: area/sync-main-<sha>")
    revision = repo.commit(suffix)
    main = repo.commit("refs/remotes/origin/main")
    repo.ancestor(revision, main)
    repo.ancestor(revision, head_sha)
    return revision


def validate(repo, event):
    try:
        pull_request = event["pull_request"]
        base = pull_request["base"]
        head = pull_request["head"]
        base_ref, head_ref = base["ref"], head["ref"]
        base_sha, head_sha = base["sha"], head["sha"]
        base_repo, head_repo = base["repo"]["full_name"], head["repo"]["full_name"]
    except (KeyError, TypeError):
        raise PolicyError("Expected a complete pull_request event") from None
    if not all(isinstance(value, str) and COMMIT.fullmatch(value) for value in (base_sha, head_sha)):
        raise PolicyError("Expected full commit SHA values")
    if not all(isinstance(value, str) and value for value in (base_repo, head_repo)):
        raise PolicyError("Expected repository names")
    area = branch_area(base_ref, head_ref)
    if base_ref == "main" and base_repo != head_repo:
        raise PolicyError("Integration PRs must come from a trunk in the same repository")
    merge_base, paths = repo.changes(base_sha, head_sha)
    before, after = repo.tree(merge_base), repo.tree(head_sha)
    synced = sync_revision(repo, area, base_ref, head_ref, head_sha)
    if synced and base_repo != head_repo:
        raise PolicyError("Sync PRs must come from the same repository")
    main_tree = repo.tree(synced) if synced else {}
    violations = []
    for path in paths:
        imported = bool(synced) and after.get(path) == main_tree.get(path)
        if not owns(area, path) and not imported:
            violations.append("Outside " + area + " scope: " + path)
        if path.startswith("backend/docs/") and path in after:
            violations.append("Local analysis material must not be committed: " + path)
        if path.lower().endswith(MARKDOWN) and path in after and not imported:
            legacy_android = area == "android" and (
                path in before or (base_ref == "main" and path in PUBLISHED_ANDROID_DOCS)
            )
            if not legacy_android:
                violations.append("Markdown must remain local: " + path)
    return violations


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", default=os.environ.get("GITHUB_EVENT_PATH"))
    parser.add_argument("--repo", default=".")
    args = parser.parse_args()
    if not args.event:
        parser.error("--event or GITHUB_EVENT_PATH is required")
    try:
        event = json.loads(Path(args.event).read_text())
        violations = validate(Repository(args.repo), event)
    except (OSError, ValueError, PolicyError) as error:
        violations = [str(error)]
    for violation in violations:
        print("Policy violation: " + json.dumps(violation, ensure_ascii=True), file=sys.stderr)
    if violations:
        return 1
    print("Branch and changed-file policy passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
