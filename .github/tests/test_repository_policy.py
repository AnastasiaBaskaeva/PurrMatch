import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


GITHUB = Path(__file__).resolve().parents[1]
SCRIPT = GITHUB / "scripts/check_repository.py"
SPEC = importlib.util.spec_from_file_location("repository_policy", SCRIPT)
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)


class BranchRoutesTest(unittest.TestCase):
    def test_route_matrix(self):
        sources = {
            "android/feature": {"android/trunk"},
            "android/sync-main-94e86a0": {"android/trunk"},
            "android/trunk-extra": {"android/trunk"},
            "backend/feature": {"backend/trunk"},
            "backend/sync-main-94e86a0": {"backend/trunk"},
            "backend/trunk-extra": {"backend/trunk"},
            "infra/docker-compose": {"infra/trunk"},
            "infra/sync-main-94e86a0": {"infra/trunk"},
            "infra/trunk-extra": {"infra/trunk"},
            "android/trunk": {"main", "android/trunk"},
            "backend/trunk": {"main", "backend/trunk"},
            "infra/trunk": {"main", "infra/trunk"},
            "main": set(), "feature/test": set(),
            "android/": set(), "backend/": set(), "infra/": set(),
            "androidish/feature": set(), "backendish/feature": set(),
            "infrax/feature": set(), "": set(),
        }
        bases = ["main", "android/trunk", "backend/trunk", "infra/trunk", "release", "infra/other", ""]
        for base in bases:
            for head, allowed in sources.items():
                if base == head and base in {"main", "android/trunk", "backend/trunk", "infra/trunk"}:
                    continue
                with self.subTest(base=base, head=head):
                    if base in allowed:
                        self.assertIn(POLICY.branch_area(base, head), POLICY.AREAS)
                    else:
                        with self.assertRaises(POLICY.PolicyError):
                            POLICY.branch_area(base, head)


class RepositoryPolicyTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="purrmatch-policy-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.commit_number = 0
        self.environment = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
        self.git("init", "-q")
        self.base = self.commit({
            "backend/app.go": "package main\n",
            "backend/Dockerfile": "FROM scratch\n",
            "android/Main.kt": "class Main\n",
            "android/README.md": "Existing Android documentation\n",
            "infra/main.tf": "variable \"folder_id\" {}\n",
            "contracts/api.json": "{}\n",
            ".github/workflows/android.yml": "name: Android\n",
            ".gitignore": "*.log\n",
        })
        self.git("update-ref", "refs/remotes/origin/main", self.base)

    def git(self, *args):
        result = subprocess.run(
            ["git", "-c", "user.name=Policy Test", "-c", "user.email=policy@example.test",
             "-c", "commit.gpgsign=false", "-c", "core.hooksPath=" + os.devnull,
             "-C", str(self.root), *args],
            env=self.environment, capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def commit(self, changes, parent=None):
        if parent:
            self.git("checkout", "--detach", "-q", parent)
        for name, content in changes.items():
            path = self.root / name
            if content is None:
                path.unlink()
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
        self.git("add", "-A")
        self.commit_number += 1
        self.git("commit", "-q", "--allow-empty", "-m", "fixture " + str(self.commit_number))
        return self.git("rev-parse", "HEAD")

    def event(self, head, area="backend", base=None, branch=None, head_repo="owner/PurrMatch"):
        return {"pull_request": {
            "base": {"ref": base or area + "/trunk", "sha": self.base,
                     "repo": {"full_name": "owner/PurrMatch"}},
            "head": {"ref": branch or area + "/feature", "sha": head,
                     "repo": {"full_name": head_repo}},
        }}

    def validate(self, event):
        return POLICY.validate(POLICY.Repository(self.root), event)

    def sync(self, main, changes=None, area="backend"):
        self.git("update-ref", "refs/remotes/origin/main", main)
        head = self.commit(changes or {}, parent=main)
        return self.event(head, area=area, branch=area + "/sync-main-" + main[:12])

    def test_own_paths_and_shared_contract(self):
        for area, path in (("android", "android/View.kt"), ("backend", "backend/api.go"), ("infra", "infra/dev")):
            with self.subTest(area=area):
                head = self.commit({path: "new\n", "contracts/api.json": '{"version":1}\n'}, parent=self.base)
                self.assertEqual(self.validate(self.event(head, area=area)), [])

    def test_cross_area_changes_are_rejected(self):
        for area, foreign in (("android", "backend/app.go"), ("backend", "android/Main.kt"), ("infra", "backend/app.go")):
            with self.subTest(area=area):
                head = self.commit({foreign: "changed\n"}, parent=self.base)
                self.assertTrue(self.validate(self.event(head, area=area)))

    def test_target_updates_do_not_become_feature_changes(self):
        head = self.commit({"backend/app.go": "backend feature\n"})
        advanced_target = self.commit({"android/Main.kt": "synced main update\n"}, parent=self.base)
        event = self.event(head)
        event["pull_request"]["base"]["sha"] = advanced_target
        self.assertEqual(self.validate(event), [])

    def test_foreign_deletion_is_rejected(self):
        head = self.commit({"android/Main.kt": None})
        self.assertTrue(self.validate(self.event(head)))

    def test_cross_area_rename_checks_source_and_destination(self):
        head = self.commit({"android/Main.kt": None, "backend/Main.kt": "class Main\n"})
        self.assertTrue(any("android/Main.kt" in error for error in self.validate(self.event(head))))

    def test_shared_docker_and_git_files_belong_to_infra(self):
        head = self.commit({"backend/Dockerfile": "FROM scratch\nUSER 10001\n", ".gitignore": "*.log\n*.out\n"})
        self.assertEqual(self.validate(self.event(head, area="infra")), [])
        self.assertTrue(self.validate(self.event(head, area="backend")))

    def test_backend_cannot_change_workflows(self):
        head = self.commit({".github/workflows/android.yml": "name: changed\n"})
        self.assertTrue(self.validate(self.event(head)))

    def test_android_can_update_its_existing_workflow(self):
        head = self.commit({".github/workflows/android.yml": "name: updated\n"})
        self.assertEqual(self.validate(self.event(head, area="android")), [])

    def test_new_markdown_is_local_in_every_feature_area(self):
        for area in POLICY.AREAS:
            for extension in (".MD", ".Markdown", ".mdown"):
                with self.subTest(area=area, extension=extension):
                    head = self.commit({area + "/PLAN" + extension: "local\n"}, parent=self.base)
                    self.assertTrue(self.validate(self.event(head, area=area)))

    def test_existing_android_docs_can_be_updated(self):
        head = self.commit({"android/README.md": "updated\n"})
        self.assertEqual(self.validate(self.event(head, area="android")), [])

    def test_initial_android_trunk_import_preserves_published_docs(self):
        head = self.commit({"android/docs/api-contract.md": "previously published Android contract\n",
                            "android/presentation/README.md": "previously published presentation docs\n"})
        self.assertEqual(self.validate(self.event(head, area="android", base="main", branch="android/trunk")), [])

    def test_android_integration_does_not_allow_arbitrary_new_markdown(self):
        head = self.commit({"android/new-plan.md": "local\n"})
        self.assertTrue(self.validate(self.event(head, area="android", base="main", branch="android/trunk")))

    def test_local_inventory_is_rejected(self):
        head = self.commit({"backend/docs/inventory.tsv": "local\n"})
        self.assertTrue(self.validate(self.event(head)))

    def test_existing_backend_markdown_is_not_published_again(self):
        old = self.commit({"backend/README.md": "old\n"})
        self.base = old
        head = self.commit({"backend/README.md": "changed\n"})
        self.assertTrue(self.validate(self.event(head)))

    def test_cleanup_can_remove_local_material(self):
        old = self.commit({"backend/docs/inventory.tsv": "old\n"})
        self.base = old
        head = self.commit({"backend/docs/inventory.tsv": None})
        self.assertEqual(self.validate(self.event(head)), [])

    def test_main_accepts_only_area_owned_integration_changes(self):
        for area, path in (("android", "android/Main.kt"), ("backend", "backend/app.go"), ("infra", "infra/main.tf")):
            with self.subTest(area=area):
                head = self.commit({path: "updated\n"}, parent=self.base)
                self.assertEqual(self.validate(self.event(head, area=area, base="main", branch=area + "/trunk")), [])
        head = self.commit({"android/Main.kt": "foreign\n"}, parent=self.base)
        self.assertTrue(self.validate(self.event(head, base="main", branch="backend/trunk")))

    def test_fork_cannot_impersonate_integration_trunk(self):
        head = self.commit({"backend/app.go": "changed\n"})
        with self.assertRaises(POLICY.PolicyError):
            self.validate(self.event(head, base="main", branch="backend/trunk", head_repo="fork/PurrMatch"))

    def test_feature_fork_still_obeys_ownership(self):
        head = self.commit({"backend/app.go": "changed\n"})
        self.assertEqual(self.validate(self.event(head, head_repo="fork/PurrMatch")), [])

    def test_sync_imports_exact_main_files_and_existing_docs(self):
        main = self.commit({"android/Main.kt": "main update\n", "android/docs/old.md": "published\n"})
        event = self.sync(main, {"backend/app.go": "backend update\n"})
        self.assertEqual(self.validate(event), [])

    def test_sync_rejects_new_foreign_edits(self):
        main = self.commit({"android/Main.kt": "main update\n"})
        event = self.sync(main, {"android/Main.kt": "unreviewed update\n"})
        self.assertTrue(self.validate(event))

    def test_sync_name_without_main_ancestry_is_not_enough(self):
        main = self.commit({"android/Main.kt": "main update\n"})
        self.git("update-ref", "refs/remotes/origin/main", main)
        head = self.commit({"android/Main.kt": "main update\n"}, parent=self.base)
        with self.assertRaises(POLICY.PolicyError):
            self.validate(self.event(head, branch="backend/sync-main-" + main[:12]))

    def test_sync_reference_must_be_on_main_history(self):
        foreign = self.commit({"android/Main.kt": "not on main\n"})
        with self.assertRaises(POLICY.PolicyError):
            self.validate(self.event(foreign, branch="backend/sync-main-" + foreign[:12]))

    def test_sync_requires_a_commit_suffix(self):
        head = self.commit({"backend/app.go": "changed\n"})
        with self.assertRaises(POLICY.PolicyError):
            self.validate(self.event(head, branch="backend/sync-main-arbitrary"))

    def test_sync_imports_foreign_deletions(self):
        main = self.commit({"android/Main.kt": None})
        self.assertEqual(self.validate(self.sync(main)), [])

    def test_sync_compares_file_modes(self):
        main = self.commit({"android/Main.kt": "main update\n"})
        self.git("update-ref", "refs/remotes/origin/main", main)
        (self.root / "android/Main.kt").chmod(0o755)
        head = self.commit({})
        event = self.event(head, branch="backend/sync-main-" + main[:12])
        self.assertTrue(self.validate(event))

    def test_sync_rejects_fork_even_with_matching_ancestry(self):
        main = self.commit({"android/Main.kt": "main update\n"})
        event = self.sync(main)
        event["pull_request"]["head"]["repo"]["full_name"] = "fork/PurrMatch"
        with self.assertRaises(POLICY.PolicyError):
            self.validate(event)

    def test_newline_in_filename_does_not_hide_foreign_path(self):
        name = "android/hidden\nbackend/file.kt"
        head = self.commit({name: "foreign\n"})
        self.assertTrue(self.validate(self.event(head)))

    def test_invalid_sha_and_payload_fail_closed(self):
        with self.assertRaises(POLICY.PolicyError):
            self.validate({})
        event = self.event(self.base)
        event["pull_request"]["head"]["sha"] = "HEAD; echo bypass"
        with self.assertRaises(POLICY.PolicyError):
            self.validate(event)

    def test_cli_reads_event_and_returns_success(self):
        head = self.commit({"backend/app.go": "changed\n"})
        event_path = self.root / "event.json"
        event_path.write_text(json.dumps(self.event(head)))
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--repo", str(self.root)],
            env=dict(self.environment, GITHUB_EVENT_PATH=str(event_path)), capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_cli_escapes_workflow_command_like_filenames(self):
        head = self.commit({"backend/hidden\n::error::injected.md": "local\n"})
        event_path = self.root / "event.json"
        event_path.write_text(json.dumps(self.event(head)))
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--repo", str(self.root), "--event", str(event_path)],
            env=self.environment, capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 1)
        self.assertEqual(len(result.stderr.splitlines()), 1)
        self.assertIn("\\n::error::", result.stderr)


class ProtectionSettingsTest(unittest.TestCase):
    def test_checks_and_history_protection_without_human_review(self):
        settings = json.loads((GITHUB / "branch-protection.json").read_text())
        self.assertEqual(set(settings["required_status_checks"]["contexts"]), {"Branch policy", "Repository checks"})
        self.assertTrue(settings["required_status_checks"]["strict"])
        self.assertTrue(settings["enforce_admins"])
        self.assertFalse(settings["allow_force_pushes"])
        self.assertFalse(settings["allow_deletions"])
        self.assertEqual(settings["required_pull_request_reviews"]["required_approving_review_count"], 0)
        self.assertFalse(settings["required_pull_request_reviews"]["require_code_owner_reviews"])


if __name__ == "__main__":
    unittest.main()
