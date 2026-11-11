#!/usr/bin/env python3
"""
Skill tooling for this repository. Standard library only; run with `python3 -I` (isolated mode, so nothing in
the working directory can shadow a standard module).

  skills_tool.py lint       [--strict] [--root DIR]   check skills, rules files and CLAUDE.md (format, budgets, IDs)
  skills_tool.py check-refs [--strict] [--root DIR]   check that every inline `token` in them still resolves

Normally called through tools/skills/lint.sh and tools/skills/check-refs.sh from the repository root.

Output: one line per finding, `ERROR|WARN <path>:<line>: <message>`, then a summary line.
Exit codes: 0 = no errors · 1 = errors (or warnings with --strict) · 2 = usage error.

The checks implement the "Tooling" rules of the skills-maintenance skill; change both together.
"""
from __future__ import annotations

import argparse
import fnmatch
import os
import re
import sys
from pathlib import Path

SKILLS_DIR = Path(".claude/skills")
RULES_DIR = Path(".claude/rules")
CLAUDE_MD = Path("CLAUDE.md")
IGNORE_FILE = Path("tools/skills/check-refs.ignore")
SPEC_COPY = Path(".arch-spec/spec")

PORTABLE_KEYS = {"name", "description", "license", "compatibility", "metadata", "allowed-tools"}
CLAUDE_KEYS = {"disable-model-invocation", "user-invocable", "argument-hint", "paths", "model"}

NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
# A rule definition: `**ID** — …` at line start or after `- `, optionally struck through (`~~**ID** — …~~`).
RULE_RE = re.compile(r"^(?:- )?(?:~~)?\*\*([A-Z][A-Z0-9]*(?:-[A-Z][A-Z0-9]*)*-\d+)\*\*\s+—")
ORIGIN_ANY_RE = re.compile(r"^<!--\s*origin:")
ORIGIN_RE = re.compile(r"^<!-- origin: (arch-spec [A-Z]+-\d+(, [A-Z]+-\d+)*|project) -->$")
OVERRIDE_START_RE = re.compile(r"^> \*\*Project override\*\*")
OVERRIDE_RE = re.compile(r"^> \*\*Project override\*\* — ([A-Z]+-\d+) — (\d{4}-\d{2}-\d{2})$")
VERSION_RE = re.compile(r"(?<![\w.])\d+\.\d+\.\d+(?!\w|\.\d)")
CONTENTS_RE = re.compile(r"^(#+\s*)?(\*\*)?contents\b", re.I)
REF_LINK_RE = re.compile(r"\]\((references/[^)#\s]+)")
FENCE_RE = re.compile(r"^\s*(```|~~~)")

KNOWN_EXT = (".kt", ".kts", ".md", ".toml", ".xml", ".json", ".yml", ".yaml", ".sh", ".py", ".properties")
SKIP_DIRS = {".git", "build", ".gradle", ".idea", ".arch-spec", "node_modules", ".cxx", ".externalNativeBuild"}


class Report:
    def __init__(self) -> None:
        self.errors = 0
        self.warnings = 0

    def error(self, path: Path | str, line: int, msg: str) -> None:
        self.errors += 1
        print(f"ERROR {path}:{line}: {msg}")

    def warn(self, path: Path | str, line: int, msg: str) -> None:
        self.warnings += 1
        print(f"WARN {path}:{line}: {msg}")

    def finish(self, tool: str, strict: bool) -> int:
        failed = self.errors > 0 or (strict and self.warnings > 0)
        print(f"{tool}: {'FAIL' if failed else 'OK'} — {self.errors} error(s), {self.warnings} warning(s)")
        return 1 if failed else 0


# ------------------------------------------------------------------------------------------------ helpers
def read_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines()


def unfenced(lines: list[str]):
    """Yields (line_number, text) for lines outside fenced code blocks."""
    in_fence = False
    for i, line in enumerate(lines, start=1):
        if FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if not in_fence:
            yield i, line


def parse_frontmatter(lines: list[str]):
    """Returns (fields, end_line, error). Supports the `key: value` subset skills use: scalars, `>`/`|` block
    scalars, indented continuation lines, `- item` lists and inline `[a, b]` lists."""
    if not lines or lines[0].strip() != "---":
        return None, 0, "no YAML frontmatter (the file must start with ---)"
    fields: dict[str, object] = {}
    key = None
    for i in range(1, len(lines)):
        raw = lines[i]
        if raw.strip() == "---":
            for k, v in list(fields.items()):
                if isinstance(v, list) and v and all(isinstance(x, str) for x in v) and k != "paths":
                    fields[k] = " ".join(x for x in v).strip()
            return fields, i + 1, None
        if "\t" in raw[: len(raw) - len(raw.lstrip())]:
            return None, i + 1, "tab used for indentation in frontmatter"
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        m = re.match(r"^([A-Za-z][\w-]*):\s*(.*)$", raw)
        if m and not raw.startswith((" ", "\t")):
            key, value = m.group(1), m.group(2).strip()
            if value in (">", "|", ">-", "|-", ">+", "|+"):
                fields[key] = []
            elif value.startswith("[") and value.endswith("]"):
                fields[key] = [x.strip().strip("'\"") for x in value[1:-1].split(",") if x.strip()]
            elif value == "":
                fields[key] = []
            else:
                fields[key] = value.strip("'\"") if value[:1] in "'\"" else value
            continue
        if key is None:
            return None, i + 1, f"cannot parse frontmatter line: {raw.strip()!r}"
        item = re.match(r"^\s+-\s+(.*)$", raw)
        current = fields.get(key)
        if item and isinstance(current, list):
            current.append(item.group(1).strip().strip("'\""))
        elif raw.startswith((" ", "\t")):
            if isinstance(current, list):
                current.append(raw.strip())
            else:
                fields[key] = f"{current} {raw.strip()}"
        else:
            return None, i + 1, f"cannot parse frontmatter line: {raw.strip()!r}"
    return None, len(lines), "frontmatter is not closed with ---"


def skill_dirs(root: Path) -> list[Path]:
    base = root / SKILLS_DIR
    return sorted(p for p in base.iterdir() if p.is_dir()) if base.is_dir() else []


def skill_markdown(root: Path) -> list[Path]:
    """SKILL.md and references of every skill (CHANGELOG.md is history, not guidance)."""
    out = []
    for d in skill_dirs(root):
        if (d / "SKILL.md").is_file():
            out.append(d / "SKILL.md")
        refs = d / "references"
        if refs.is_dir():
            out += sorted(p for p in refs.glob("*.md") if p.is_file())
    return out


def rules_files(root: Path) -> list[Path]:
    base = root / RULES_DIR
    return sorted(base.glob("*.md")) if base.is_dir() else []


# ------------------------------------------------------------------------------------------------ lint
def lint(root: Path, strict: bool) -> int:
    rep = Report()
    rule_sites: dict[str, list[str]] = {}

    for d in skill_dirs(root):
        skill = d / "SKILL.md"
        rel_dir = d.relative_to(root)
        if not skill.is_file():
            rep.error(rel_dir, 0, "skill folder without SKILL.md")  # check 1
            continue
        rel = skill.relative_to(root)
        lines = read_lines(skill)
        fm, _, err = parse_frontmatter(lines)
        if err:
            rep.error(rel, 1, err)  # check 1
            fm = {}
        name = fm.get("name")  # check 2
        if not name:
            rep.error(rel, 1, "frontmatter has no name")
        else:
            if name != d.name:
                rep.error(rel, 1, f"name '{name}' differs from folder '{d.name}'")
            if not NAME_RE.match(str(name)) or len(str(name)) > 64:
                rep.error(rel, 1, f"name '{name}' must match {NAME_RE.pattern} and be <= 64 chars")
        desc = fm.get("description")  # check 3
        if isinstance(desc, list):
            desc = " ".join(desc)
        if not desc:
            rep.error(rel, 1, "frontmatter has no description")
        elif len(str(desc)) > 1024:
            rep.error(rel, 1, f"description is {len(str(desc))} chars (> 1024)")
        for k in fm:  # check 4
            if k not in PORTABLE_KEYS | CLAUDE_KEYS:
                rep.warn(rel, 1, f"frontmatter key '{k}' is not a known skill key")
        n = len(lines)  # check 5
        if n > 500:
            rep.error(rel, n, f"{n} lines (> 500): move detail into references/")
        elif n > 300:
            rep.warn(rel, n, f"{n} lines (> 300): move detail into references/")

        refs = d / "references"
        linked = set()
        for i, line in enumerate(lines, start=1):  # check 7 (links resolve)
            for target in REF_LINK_RE.findall(line):
                linked.add(target)
                if not (d / target).is_file():
                    rep.error(rel, i, f"link to missing {target}")
        if refs.is_dir():
            for sub in sorted(refs.iterdir()):  # check 6
                if sub.is_dir():
                    rep.error(sub.relative_to(root), 0, "references/ must be one level deep (no subfolders)")
            for ref in sorted(refs.glob("*.md")):
                rrel = ref.relative_to(root)
                if f"references/{ref.name}" not in linked:  # check 7 (every reference linked)
                    rep.error(rrel, 0, "not linked from SKILL.md (add it to the references index)")
                rlines = read_lines(ref)  # check 8
                rn = len(rlines)
                if rn > 500:
                    rep.error(rrel, rn, f"{rn} lines (> 500): split it")
                elif rn > 300:
                    rep.warn(rrel, rn, f"{rn} lines (> 300)")
                if rn > 100 and not any(CONTENTS_RE.match(l.strip()) for l in rlines[:20]):
                    rep.warn(rrel, 1, f"{rn} lines without a contents list near the top")

    for path in skill_markdown(root):
        rel = path.relative_to(root)
        lines = read_lines(path)
        lint_rules_and_overrides(rep, rel, lines, rule_sites)  # checks 9-11
        for i, line in enumerate(lines, start=1):  # check 12
            if VERSION_RE.search(line):
                rep.warn(rel, i, "version-like string — point to gradle/libs.versions.toml instead")
            if "no-commit-files" in line:
                rep.warn(rel, i, "skills must not reference the git-excluded scratch folder")
    for path in rules_files(root) + ([root / CLAUDE_MD] if (root / CLAUDE_MD).is_file() else []):
        lint_rules_and_overrides(rep, path.relative_to(root), read_lines(path), rule_sites)

    for rule_id, sites in sorted(rule_sites.items()):  # check 9
        if len(sites) > 1:
            rep.error(sites[1].rsplit(":", 1)[0], int(sites[1].rsplit(":", 1)[1]),
                      f"rule {rule_id} defined more than once: {', '.join(sites)}")

    claude = root / CLAUDE_MD  # check 13
    if claude.is_file():
        n = len(read_lines(claude))
        if n > 200:
            rep.error(CLAUDE_MD, n, f"{n} lines (> 200): move procedures into skills")
        elif n > 150:
            rep.warn(CLAUDE_MD, n, f"{n} lines (> 150): move procedures into skills")
    else:
        rep.error(CLAUDE_MD, 0, "missing")

    for path in rules_files(root):  # check 14
        rel = path.relative_to(root)
        fm, _, err = parse_frontmatter(read_lines(path))
        if err:
            rep.error(rel, 1, err)
            continue
        paths = fm.get("paths")
        if isinstance(paths, str):
            paths = [paths]
        if not paths or not any(str(p).strip() for p in paths):
            rep.error(rel, 1, "rules file needs a non-empty `paths:` list")
    return rep.finish("lint", strict)


def lint_rules_and_overrides(rep: Report, rel: Path, lines: list[str], rule_sites: dict) -> None:
    body = list(unfenced(lines))
    ids_in_file = set()
    for idx, (i, line) in enumerate(body):
        m = RULE_RE.match(line)
        if not m:
            continue
        rule_id = m.group(1)
        ids_in_file.add(rule_id)
        rule_sites.setdefault(rule_id, []).append(f"{rel}:{i}")
        origin = None  # check 10: an origin marker before the next rule / heading closes the rule
        for j, nxt in body[idx + 1:]:
            s = nxt.strip()
            if ORIGIN_ANY_RE.match(s):
                origin = (j, s)
                break
            if RULE_RE.match(nxt) or s.startswith("#") or OVERRIDE_START_RE.match(s):
                break
        if origin is None:
            rep.warn(rel, i, f"rule {rule_id} has no origin marker (<!-- origin: arch-spec ID --> or <!-- origin: project -->)")
        elif not ORIGIN_RE.match(origin[1]):
            rep.warn(rel, origin[0], f"malformed origin marker: {origin[1]}")
    for idx, (i, line) in enumerate(body):  # check 11
        if not OVERRIDE_START_RE.match(line.strip()):
            continue
        m = OVERRIDE_RE.match(line.strip())
        if not m:
            rep.error(rel, i, "override header must be `> **Project override** — <ID> — <YYYY-MM-DD>`")
            continue
        # The block is the run of `>` quote lines after the header, however long the Rule is.
        following = []
        for _, t in body[idx + 1:]:
            if not t.strip().startswith(">"):
                break
            following.append(t.strip())
        if not any(t.startswith("> Rule:") for t in following) or not any(t.startswith("> Reason:") for t in following):
            rep.error(rel, i, "override block needs `> Rule:` and `> Reason:` lines")
        if m.group(1) not in ids_in_file:
            rep.error(rel, i, f"override names {m.group(1)}, which is not defined in this file")


# ------------------------------------------------------------------------------------------------ check-refs
TOKEN_RE = re.compile(r"`([^`\n]+)`")
DOUBLE_TICK_RE = re.compile(r"``.+?``")
MODULE_RE = re.compile(r"^:[a-z0-9][a-z0-9-]*(:[a-z0-9][a-z0-9-]*)*$")
TASKISH_RE = re.compile(r"^:[a-z0-9][a-z0-9-]*(:[a-z0-9][a-z0-9-]*)*:[A-Za-z][A-Za-z0-9]*$")
TYPE_RE = re.compile(r"^@?[A-Z][A-Za-z0-9]*(\.[A-Z][A-Za-z0-9]*)*$")
TEMPLATE_CHARS = ("<", ">", "{{", "…", "$", "://")
CODE_CHARS = ("(", ")", "=", '"', "'", "{", "}", ";", ",", "|", "[", "]", "!", "#", "+", "&")
DECL_RE = re.compile(r"\b(?:class|interface|object|typealias)\s+([A-Z][A-Za-z0-9_]*)")
FUN_DECL_RE = re.compile(r"\bfun\s+(?:<[^>]*>\s+)?(?:[\w.]+\.)?([A-Z][A-Za-z0-9_]*)\s*\(")
VAL_DECL_RE = re.compile(r"\b(?:val|var)\s+([A-Z][A-Za-z0-9_]*)\b")
SPEC_PREFIXES = ("specs/", "installer/", "payload/")


class Repo:
    """Lazily built indexes of the repository: included modules, file basenames, declared Kotlin names."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self._modules = None
        self._basenames = None
        self._types = None
        self._relpaths = None

    def walk(self):
        for dirpath, dirnames, filenames in os.walk(self.root):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
            for f in filenames:
                yield Path(dirpath) / f

    @property
    def modules(self) -> set[str]:
        if self._modules is None:
            self._modules = set()
            settings = self.root / "settings.gradle.kts"
            if settings.is_file():
                text = settings.read_text(encoding="utf-8")
                text = re.sub(r"(?m)^\s*//.*$", "", text)
                for call in re.findall(r"include\s*\(([^)]*)\)", text):
                    self._modules.update(re.findall(r"\"(:[^\"]+)\"", call))
                # Included builds (`includeBuild("build-logic")`): their projects are addressed as
                # `:build-logic:<project>` from the root build, e.g. `./gradlew :build-logic:convention:test`.
                for name in re.findall(r"includeBuild\s*\(\s*\"([^\"]+)\"\s*\)", text):
                    build = Path(name).name
                    self._modules.add(f":{build}")
                    nested = self.root / name / "settings.gradle.kts"
                    if nested.is_file():
                        nested_text = re.sub(r"(?m)^\s*//.*$", "", nested.read_text(encoding="utf-8"))
                        for call in re.findall(r"include\s*\(([^)]*)\)", nested_text):
                            self._modules.update(f":{build}{m}" for m in re.findall(r"\"(:[^\"]+)\"", call))
        return self._modules

    @property
    def basenames(self) -> set[str]:
        if self._basenames is None:
            self._basenames = {p.name for p in self.walk()}
        return self._basenames

    @property
    def relpaths(self) -> list[str]:
        """Every file and directory, as a repository-relative posix path (for partial-path matches)."""
        if self._relpaths is None:
            out = set()
            for p in self.walk():
                rel = p.relative_to(self.root).as_posix()
                out.add(rel)
                parts = rel.split("/")
                for i in range(1, len(parts)):
                    out.add("/".join(parts[:i]))
            self._relpaths = sorted(out)
        return self._relpaths

    @property
    def types(self) -> set[str]:
        if self._types is None:
            self._types = set()
            for p in self.walk():
                if p.suffix == ".kt" and "/src/" in p.as_posix():
                    try:
                        text = p.read_text(encoding="utf-8")
                    except (UnicodeDecodeError, OSError):
                        continue
                    for rx in (DECL_RE, FUN_DECL_RE, VAL_DECL_RE):
                        self._types.update(rx.findall(text))
        return self._types


def load_ignore(root: Path) -> tuple[set[str], list[re.Pattern]]:
    exact, patterns = set(), []
    path = root / IGNORE_FILE
    if path.is_file():
        for raw in read_lines(path):
            s = raw.strip()
            if not s or s.startswith("#"):
                continue
            if s.startswith("^"):
                patterns.append(re.compile(s))
            else:
                exact.add(s)
    return exact, patterns


def path_exists(repo: Repo, token: str) -> bool:
    """A path resolves from the repository root; `specs/…`, `installer/…` and `payload/…` also against the spec
    copy in .arch-spec/spec/; a bare file name (no `/`) or a partial path anywhere in the repository; globs need
    >= 1 match."""
    t = token.rstrip("/")
    bases = [repo.root]
    if t.startswith(SPEC_PREFIXES):
        bases.append(repo.root / SPEC_COPY)
    is_glob = any(ch in t for ch in "*?[")
    if "/" not in t:
        if is_glob:
            return any(fnmatch.fnmatch(b, t) for b in repo.basenames)
        return (repo.root / t).exists() or t in repo.basenames or any(r.endswith("/" + t) for r in repo.relpaths)
    for base in bases:
        if is_glob:
            try:
                if next(iter(base.glob(t)), None) is not None:
                    return True
            except (ValueError, NotImplementedError):
                return False
        elif (base / t).exists():
            return True
    # A partial path (`_harness/fake_backend.py`, `application-architecture/references/x.md`) resolves when some
    # repository path ends with it — a moved or renamed file still fails.
    suffix = "/" + t
    if is_glob:
        return any(fnmatch.fnmatch(r, "*" + suffix) for r in repo.relpaths)
    return any(r.endswith(suffix) for r in repo.relpaths)


def classify(token: str) -> str | None:
    if token.startswith(("./gradlew", "gradlew ")):
        return "gradlew"
    if " " in token:
        return "command"
    if TASKISH_RE.match(token) or MODULE_RE.match(token):
        return "module"
    if "/" in token or token.endswith(KNOWN_EXT):
        return "path"
    if TYPE_RE.match(token) and re.search(r"[a-z]", token):
        return "type"
    return None


def check_refs(root: Path, strict: bool) -> int:
    rep = Report()
    repo = Repo(root)
    exact_ignore, regex_ignore = load_ignore(root)

    def ignored(token: str) -> bool:
        return token in exact_ignore or any(p.search(token) for p in regex_ignore)

    def check_module(rel, i, token):
        if token in repo.modules:
            return
        if TASKISH_RE.match(token):
            module = token.rsplit(":", 1)[0]
            if module in repo.modules:
                return
            rep.error(rel, i, f"task `{token}`: module {module} is not included in settings.gradle.kts")
            return
        rep.error(rel, i, f"module `{token}` is not included in settings.gradle.kts")

    def check_path(rel, i, token):
        if token.startswith(("/", "-", "~")) or token in (".", "..") or re.fullmatch(r"\.\w+", token):
            return  # absolute paths, URL paths, flags, bare extensions: not repository references
        if not path_exists(repo, token):
            rep.error(rel, i, f"path `{token}` does not exist")

    files = skill_markdown(root) + rules_files(root) + ([root / CLAUDE_MD] if (root / CLAUDE_MD).is_file() else [])
    for path in files:
        rel = path.relative_to(root)
        for i, line in unfenced(read_lines(path)):
            if "<!-- check-refs: ignore -->" in line:
                continue
            for raw in TOKEN_RE.findall(DOUBLE_TICK_RE.sub("", line)):
                token = raw.strip()
                if not token or ignored(token) or any(c in token for c in TEMPLATE_CHARS):
                    continue
                kind = classify(token)
                if kind == "gradlew":
                    for word in token.split()[1:]:
                        if word.startswith(":") and not ignored(word) and (MODULE_RE.match(word) or TASKISH_RE.match(word)):
                            check_module(rel, i, word)
                    continue
                if kind == "command":
                    words = token.split()
                    if words[0] in ("bash", "sh", "python3", "python") and not any(c in token for c in CODE_CHARS):
                        for word in words[1:]:
                            if not word.startswith("-") and not ignored(word) and ("/" in word or word.endswith(KNOWN_EXT)):
                                check_path(rel, i, word)
                    continue
                if any(c in token for c in CODE_CHARS):
                    continue  # a code snippet, not a reference
                if kind == "module":
                    check_module(rel, i, token)
                elif kind == "path":
                    check_path(rel, i, token)
                elif kind == "type":
                    name = token.lstrip("@").split(".")[-1]
                    if name not in repo.types and not ignored(name):
                        rep.warn(rel, i, f"type `{token}` is not declared in any src/**/*.kt (or add it to {IGNORE_FILE})")
    return rep.finish("check-refs", strict)


# ------------------------------------------------------------------------------------------------ main
def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["lint", "check-refs"])
    ap.add_argument("--strict", action="store_true", help="treat warnings as errors")
    ap.add_argument("--root", type=Path, default=Path("."), help="repository root (default: current directory)")
    try:
        a = ap.parse_args(argv)
    except SystemExit as e:
        return 2 if e.code else 0
    root = a.root.resolve()
    if not (root / "settings.gradle.kts").is_file() and not (root / CLAUDE_MD).is_file():
        print(f"usage error: {root} does not look like the repository root (no settings.gradle.kts / CLAUDE.md)",
              file=sys.stderr)
        return 2
    return lint(root, a.strict) if a.command == "lint" else check_refs(root, a.strict)


if __name__ == "__main__":
    sys.exit(main())
