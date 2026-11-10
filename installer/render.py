#!/usr/bin/env python3
"""
Deterministic renderer for android-app-arch-spec payloads. Standard library only.

The agent following INSTALL.md decides WHAT to install and asks the questions; this script does the mechanical,
error-prone part identically every time: placeholder substitution (contents + paths), idempotent writes with
checksums, marker-block edits, and bookkeeping in the target's `.arch-spec/state.json`.

Layout in a target project:
  .arch-spec/spec/        a copy of this spec repo (replaced wholesale on upgrade)
  .arch-spec/state.json   answers, installed components, deferred items, per-file checksums (written by this script)
  .arch-spec/baseline/    pristine copy of every file this script rendered (base for 3-way upgrades)

Usage (run from anywhere; paths are explicit):
  render.py init       --target DIR                       create .arch-spec/state.json with derived defaults
  render.py set        --target DIR KEY=VALUE ...         record answers (validated); KEY=- marks it skipped
  render.py plan       --target DIR COMPONENT             dry-run: what would be written / conflict
  render.py install    --target DIR COMPONENT [--fresh]   render a component into the target
  render.py scaffold-feature --target DIR NAME            render the feature trio template for feature NAME
  render.py rerender   --target DIR KEY [KEY ...]         re-render unmodified files that use these placeholders
  render.py status     --target DIR                       installed components, deferred items, modified files, gates
  render.py snapshot   --target DIR --out EMPTY_DIR       render what THIS spec produces for the project ("theirs")
  render.py relocate   --target DIR OLD NEW               record a renamed module dir (after `git mv OLD NEW`)
  render.py rebase     --target DIR --snapshot DIR        finish an upgrade: snapshot → baseline, re-record checksums
  render.py --self-check                                  validate this spec repo (placeholders, manifest, payload)

Exit codes: 0 ok · 1 usage/validation error · 2 conflicts were written as *.arch-spec-new (agent must resolve).
"""
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import os
import re
import sys
from pathlib import Path

SPEC_ROOT = Path(__file__).resolve().parent.parent
PAYLOAD = SPEC_ROOT / "payload"
MANIFEST = SPEC_ROOT / "installer" / "components.yml"
PARAMS_DOC = SPEC_ROOT / "installer" / "parameters.md"
STATE_REL = Path(".arch-spec") / "state.json"
BASELINE_REL = Path(".arch-spec") / "baseline"
PLACEHOLDER = re.compile(r"\{\{([A-Z][A-Z0-9_]*)\}\}")
FEATURE_TEMPLATE_REL = "architecture/feature-template"

# Safe placeholders: every value here renders to code that compiles. `None` = derived or must be provided.
DEFAULTS: dict[str, str | None] = {
    "APP_NAME": "My App",
    "PROJECT_SLUG": "my-app",
    "APPLICATION_ID": "com.example.myapp",
    "APP_PACKAGE": None,          # = APPLICATION_ID
    "ROOT_PACKAGE": None,         # = APP_PACKAGE minus last segment
    "CONVENTION_PREFIX": None,    # = last segment of ROOT_PACKAGE
    "THEME_PREFIX": None,         # = APP_NAME in PascalCase
    "MIN_SDK": "24",
    "TARGET_SDK": "36",
    "COMPILE_SDK": "37",
    "JVM_TARGET": "11",
    "BASE_LOCALE": "en",
    "EXTRA_LOCALES": "",
    "COLOR_PRIMARY": "#3D5AFE",
    "COLOR_ERROR": "#D32F2F",
    "COLOR_SUCCESS": "#2E7D32",
    "COLOR_WARNING": "#ED6C02",
    "FONT_FAMILY": "system",
    "REFERENCE_DEVICE_DP": "360x640",
    "THEME_MODE": "light-only",
    "FIGMA_URL": "",
    "BASE_URL_DEBUG": "https://api.example.invalid/",
    "BASE_URL_RELEASE": None,     # = BASE_URL_DEBUG
    "HEALTH_PATH": "health",
    "DEFAULT_BRANCH": "main",
    "KEYSTORE_FILE": None,        # = <PROJECT_SLUG>-release.jks
    "CI_BOT_PUSH": "none",
    "JDK_CI": "17",
}

# Deferred items: an item is deferred when ANY of its parameters fell back to its safe placeholder AND the parameter
# belongs to an installed component (an item of a component that is not installed is not "deferred", it is absent).
# `how` is the one line the installer shows the user; R = `python3 .arch-spec/spec/installer/render.py --target .`
DEFERRED_ITEMS: dict[str, dict] = {
    "design.palette": {"params": ["COLOR_PRIMARY", "COLOR_ERROR", "COLOR_SUCCESS", "COLOR_WARNING"],
                       "how": "R set COLOR_PRIMARY=#RRGGBB [COLOR_ERROR=… COLOR_SUCCESS=… COLOR_WARNING=…], then "
                              "R rerender COLOR_PRIMARY COLOR_ERROR COLOR_SUCCESS COLOR_WARNING"},
    "design.typography": {"params": ["FONT_FAMILY"],
                          "how": "drop the font files into lib-android/src/main/res/font/, wire them in Type.kt "
                                 "(see installer/components/architecture.md), then R set FONT_FAMILY=<name>"},
    "design.referenceDevice": {"params": ["REFERENCE_DEVICE_DP"],
                               "how": "R set REFERENCE_DEVICE_DP=<w>x<h>, then R rerender REFERENCE_DEVICE_DP"},
    "design.themeMode": {"params": ["THEME_MODE"],
                         "how": "R set THEME_MODE=light-dark (or light-only), then R rerender THEME_MODE"},
    "design.figma": {"params": ["FIGMA_URL"], "how": "R set FIGMA_URL=<link> (recorded for agents; nothing is rendered)"},
    "locales.extra": {"params": ["EXTRA_LOCALES"],
                      "how": "create values-<locale>/strings.xml in :app and every :x-ui module with strings "
                             "(same keys), then R set EXTRA_LOCALES=<a,b>"},
    "network.baseUrl": {"params": ["BASE_URL_DEBUG", "BASE_URL_RELEASE"],
                        "how": "R set BASE_URL_DEBUG=https://…/ BASE_URL_RELEASE=https://…/, then "
                               "R rerender BASE_URL_DEBUG BASE_URL_RELEASE"},
    "connectivity.healthPath": {"params": ["HEALTH_PATH"],
                                "how": "R set HEALTH_PATH=<path>, then R rerender HEALTH_PATH"},
    "ci.signing": {"params": ["KEYSTORE_FILE"],
                   "how": "create the release keystore + keystore.properties (never committed; see "
                          "keystore.properties.template), add CI secrets per ci_runbook.md; until then release "
                          "builds are signed with the debug key"},
    "ci.bot": {"params": ["CI_BOT_PUSH"],
               "how": "create the GitHub App + secrets (ci_runbook.md), then set repo variable CI_BOT_PUSH=github-app "
                      "(no file change needed)"},
}
DEFERRABLE = {p: item for item, spec in DEFERRED_ITEMS.items() for p in spec["params"]}


DERIVED = {"HEALTH_PATH_SLUG", "STOCK_APP_PACKAGE_PATH", "ROOT_PACKAGE_PATH", "APP_PACKAGE_PATH", "CONVENTION_PREFIX_PASCAL", "THEME_PREFIX_LOWER",
           "REFERENCE_WIDTH_DP", "REFERENCE_HEIGHT_DP",
           "FEATURE", "FEATURE_PASCAL", "FEATURE_CAMEL", "FEATURE_PKG"}

# Derived placeholders a `rerender KEY` must also refresh when KEY changes.
DERIVED_FROM = {"REFERENCE_DEVICE_DP": ["REFERENCE_WIDTH_DP", "REFERENCE_HEIGHT_DP"]}

VALIDATORS = {
    "APPLICATION_ID": r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$",
    "APP_PACKAGE": r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$",
    "ROOT_PACKAGE": r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$",
    "CONVENTION_PREFIX": r"^[a-z][a-z0-9]*$",
    "PROJECT_SLUG": r"^[a-z][a-z0-9-]*$",
    "THEME_PREFIX": r"^[A-Z][A-Za-z0-9]*$",
    "MIN_SDK": r"^\d+$", "TARGET_SDK": r"^\d+$", "COMPILE_SDK": r"^\d+$", "JVM_TARGET": r"^\d+$",
    "COLOR_PRIMARY": r"^#[0-9A-Fa-f]{6}$", "COLOR_ERROR": r"^#[0-9A-Fa-f]{6}$",
    "COLOR_SUCCESS": r"^#[0-9A-Fa-f]{6}$", "COLOR_WARNING": r"^#[0-9A-Fa-f]{6}$",
    "REFERENCE_DEVICE_DP": r"^\d+x\d+$",
    "THEME_MODE": r"^(light-only|light-dark)$",
    "BASE_URL_DEBUG": r"^https?://.+/$", "BASE_URL_RELEASE": r"^https?://.+/$",
    "CI_BOT_PUSH": r"^(github-app|none)$",
    "FEATURE": r"^[a-z][a-z0-9-]*$",
    # Rendered into strings.xml and Kotlin string literals unescaped — keep it free of XML/Kotlin specials.
    "APP_NAME": r"^[^'\"&<>\\$]+$",
}

MARKERS = {
    # name -> (file, comment prefix)
    "includes": ("settings.gradle.kts", "//"),
    "app-dependencies": ("app/build.gradle.kts", "//"),
}

BINARY_EXT = {".png", ".jpg", ".jpeg", ".webp", ".ttf", ".otf", ".jar", ".jks", ".gif", ".ico"}


# --------------------------------------------------------------------------------------------- tiny YAML subset
def parse_yaml(text: str):
    """Parses the subset components.yml uses: nested maps, `- item` lists, inline [a, b] lists, scalars, comments."""
    lines = []
    for raw in text.splitlines():
        stripped = _strip_comment(raw)
        if stripped.strip():
            lines.append((len(stripped) - len(stripped.lstrip(" ")), stripped.strip()))
    value, _ = _parse_block(lines, 0, 0)
    return value


def _strip_comment(line: str) -> str:
    out, quote = [], None
    for i, ch in enumerate(line):
        if quote:
            if ch == quote:
                quote = None
        elif ch in "'\"":
            quote = ch
        elif ch == "#" and (i == 0 or line[i - 1] in " \t"):
            break
        out.append(ch)
    return "".join(out).rstrip()


def _scalar(s: str):
    s = s.strip()
    if s.startswith("[") and s.endswith("]"):
        inner = s[1:-1].strip()
        return [_scalar(x) for x in _split_inline(inner)] if inner else []
    if len(s) >= 2 and s[0] == s[-1] and s[0] in "'\"":
        return s[1:-1]
    return s


def _split_inline(s: str):
    parts, buf, quote = [], [], None
    for ch in s:
        if quote:
            buf.append(ch)
            if ch == quote:
                quote = None
        elif ch in "'\"":
            quote = ch
            buf.append(ch)
        elif ch == ",":
            parts.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    parts.append("".join(buf))
    return [p.strip() for p in parts if p.strip()]


def _parse_block(lines, i, indent):
    if i >= len(lines):
        return None, i
    if lines[i][1].startswith("- "):
        items = []
        while i < len(lines) and lines[i][0] == indent and lines[i][1].startswith("- "):
            text = lines[i][1][2:]
            i += 1
            if re.match(r"^[A-Za-z_][\w-]*:(\s|$)", text):
                # A list item that is a map (`- file: x` + more keys indented under it), as `inserts` uses.
                sub = [(indent + 2, text)]
                while i < len(lines) and lines[i][0] > indent:
                    sub.append(lines[i])
                    i += 1
                items.append(_parse_block(sub, 0, indent + 2)[0])
            else:
                items.append(_scalar(text))
        return items, i
    result = {}
    while i < len(lines) and lines[i][0] == indent:
        key, _, rest = lines[i][1].partition(":")
        i += 1
        if rest.strip():
            result[key.strip()] = _scalar(rest)
        elif i < len(lines) and lines[i][0] > indent:
            result[key.strip()], i = _parse_block(lines, i, lines[i][0])
        elif i < len(lines) and lines[i][0] == indent and lines[i][1].startswith("- "):
            result[key.strip()], i = _parse_block(lines, i, indent)
        else:
            result[key.strip()] = None
    return result, i


def load_manifest() -> dict:
    return parse_yaml(MANIFEST.read_text())["components"]


# --------------------------------------------------------------------------------------------- values
def pascal(s: str) -> str:
    return "".join(p[:1].upper() + p[1:] for p in re.split(r"[^A-Za-z0-9]+", s) if p)


def resolve_values(answers: dict, feature: str | None = None) -> dict:
    v = {k: (answers.get(k) if answers.get(k) not in (None, "-") else d) for k, d in DEFAULTS.items()}
    v["APP_PACKAGE"] = v["APP_PACKAGE"] or v["APPLICATION_ID"]
    v["ROOT_PACKAGE"] = v["ROOT_PACKAGE"] or (v["APP_PACKAGE"].rsplit(".", 1)[0] if "." in v["APP_PACKAGE"] else v["APP_PACKAGE"])
    v["CONVENTION_PREFIX"] = v["CONVENTION_PREFIX"] or re.sub(r"[^a-z0-9]", "", v["ROOT_PACKAGE"].rsplit(".", 1)[-1]) or "app"
    v["THEME_PREFIX"] = v["THEME_PREFIX"] or (pascal(v["APP_NAME"]) or "App")
    if answers.get("PROJECT_SLUG") in (None, "-"):
        v["PROJECT_SLUG"] = re.sub(r"[^a-z0-9]+", "-", v["APP_NAME"].lower()).strip("-") or "my-app"
    v["BASE_URL_RELEASE"] = v["BASE_URL_RELEASE"] or v["BASE_URL_DEBUG"]
    v["KEYSTORE_FILE"] = v["KEYSTORE_FILE"] or f'{v["PROJECT_SLUG"]}-release.jks'
    # mitm scenario folders are named after the request path: `v1/health` → `v1_health` (mitm-testing convention).
    v["HEALTH_PATH_SLUG"] = re.sub(r"[^A-Za-z0-9_-]+", "_", v["HEALTH_PATH"].strip("/")) or "health"
    v["ROOT_PACKAGE_PATH"] = v["ROOT_PACKAGE"].replace(".", "/")
    v["APP_PACKAGE_PATH"] = v["APP_PACKAGE"].replace(".", "/")
    v["CONVENTION_PREFIX_PASCAL"] = pascal(v["CONVENTION_PREFIX"])
    v["THEME_PREFIX_LOWER"] = v["THEME_PREFIX"].lower()
    v["REFERENCE_WIDTH_DP"], _, v["REFERENCE_HEIGHT_DP"] = v["REFERENCE_DEVICE_DP"].partition("x")
    if feature:
        v["FEATURE"] = feature
        v["FEATURE_PASCAL"] = pascal(feature)
        v["FEATURE_CAMEL"] = v["FEATURE_PASCAL"][:1].lower() + v["FEATURE_PASCAL"][1:]
        v["FEATURE_PKG"] = feature.replace("-", "")
    return v


def substitute(text: str, values: dict, origin: str) -> str:
    def repl(m):
        key = m.group(1)
        if key not in values or values[key] is None:
            raise SystemExit(f"error: placeholder {{{{{key}}}}} has no value (in {origin})")
        return str(values[key])
    return PLACEHOLDER.sub(repl, text)


# --------------------------------------------------------------------------------------------- state
def state_path(target: Path) -> Path:
    return target / STATE_REL


def load_state(target: Path) -> dict:
    p = state_path(target)
    if not p.exists():
        raise SystemExit(f"error: {p} not found — run `render.py init --target {target}` first")
    return json.loads(p.read_text())


def save_state(target: Path, state: dict) -> None:
    p = state_path(target)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n")


def spec_version() -> str:
    vf = SPEC_ROOT / "VERSION"
    return vf.read_text().strip() if vf.exists() else "0.0.0"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


CORE_COMPONENTS = ("foundation", "ai-layer", "architecture", "docs")


def compute_deferred(answers: dict, installed) -> dict:
    """Deferred items of the installed components (plus Core, which a fresh install always gets)."""
    manifest = load_manifest()
    relevant = set()
    for name in set(installed) | set(CORE_COMPONENTS):
        relevant.update((manifest.get(name) or {}).get("parameters") or [])
    out = {}
    for item, spec in DEFERRED_ITEMS.items():
        # An explicit empty answer ("no extra locales") is an answer; only unanswered or skipped ("-") defers.
        missing = [p for p in spec["params"] if p in relevant and answers.get(p) in (None, "-")]
        if missing:
            out[item] = {"placeholder_used_for": missing, "how": spec["how"]}
    return out


# --------------------------------------------------------------------------------------------- rendering
def iter_payload(component_payload: str, exclude_feature_template: bool):
    root = PAYLOAD / component_payload
    if not root.exists():
        raise SystemExit(f"error: payload directory {root} does not exist")
    for path in sorted(root.rglob("*")):
        if path.is_dir() or path.name == ".DS_Store":
            continue
        rel = path.relative_to(root).as_posix()
        if exclude_feature_template and (component_payload + "/" + rel).startswith(FEATURE_TEMPLATE_REL + "/"):
            continue
        yield path, rel


def render_file(src: Path, rel: str, values: dict) -> tuple[str, bytes]:
    dest_rel = substitute(rel, values, f"path {rel}")
    if dest_rel.endswith(".tmpl"):
        dest_rel = dest_rel[: -len(".tmpl")]
    data = src.read_bytes()
    if src.suffix.lower() not in BINARY_EXT:
        data = substitute(data.decode("utf-8"), values, str(src.relative_to(SPEC_ROOT))).encode("utf-8")
    return dest_rel, data


def relocate_path(state: dict, rel: str) -> str:
    """Maps a rendered path through the project's recorded relocations (module renames, see `relocate`)."""
    for r in state.get("relocations", []):
        if rel == r["from"].rstrip("/") or rel.startswith(r["from"]):
            return r["to"] + rel[len(r["from"]):]
    return rel


def relocate_line(state: dict, line: str) -> str:
    """Maps module references in a marker-insert line (`":old"`, `":old:`) through the recorded relocations."""
    for r in state.get("relocations", []):
        old, new = r["from"].rstrip("/"), r["to"].rstrip("/")
        line = re.sub(r'(["\s(]):' + re.escape(old) + r'(?=["\s):])', lambda m: m.group(1) + ":" + new, line)
    return line


def write_baseline(target: Path, dest_rel: str, data: bytes) -> None:
    # Pristine copy of what the spec rendered: the "base" of the 3-way comparison an upgrade performs
    # (base = this copy, ours = the project's current file, theirs = `snapshot` of the newer spec).
    base = target / BASELINE_REL / dest_rel
    base.parent.mkdir(parents=True, exist_ok=True)
    base.write_bytes(data)


def write_planned(target: Path, state: dict, planned: list, fresh_replaces: set, dry_run: bool,
                  baseline: bool = True) -> dict:
    report = {"written": [], "unchanged": [], "conflicts": []}
    files = state.setdefault("files", {})
    for dest_rel, data, source in planned:
        dest_rel = relocate_path(state, dest_rel)
        dest = target / dest_rel
        recorded = files.get(dest_rel, {}).get("sha")
        if dest.exists():
            current = sha(dest.read_bytes())
            if current == sha(data):
                report["unchanged"].append(dest_rel)
                if not dry_run:
                    files[dest_rel] = {"sha": current, "source": source}
                    if baseline:
                        write_baseline(target, dest_rel, data)
                continue
            if recorded != current and dest_rel not in fresh_replaces:
                report["conflicts"].append(dest_rel)
                if not dry_run:
                    Path(str(dest) + ".arch-spec-new").write_bytes(data)
                continue
        report["written"].append(dest_rel)
        if not dry_run:
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
            if baseline:
                write_baseline(target, dest_rel, data)
            if dest.suffix == ".sh" or dest.name == "gradlew":
                dest.chmod(0o755)
            files[dest_rel] = {"sha": sha(data), "source": source}
    return report


STRICT_MARKER_FILES = {"settings.gradle.kts", "app/build.gradle.kts"}


def apply_markers(target: Path, state: dict, inserts: list, dry_run: bool, baseline: bool = True) -> list:
    """inserts: [(file_rel, marker, [lines])]. Lines are unique-merged inside `<arch-spec:marker>` blocks.

    A missing Gradle file is an error (foundation must precede everything); any other missing file is skipped —
    its lines are applied later, when the component that owns the file is installed (all inserts are re-applied)."""
    touched = []
    by_key: dict = {}
    for file_rel, marker, lines in inserts:
        file_rel = relocate_path(state, file_rel)
        by_key.setdefault((file_rel, marker), []).extend(relocate_line(state, l) for l in lines)
    if baseline and not dry_run and (target / BASELINE_REL).is_dir():
        # The baseline holds what the spec produced INCLUDING its own marker lines, so an upgrade compares like with
        # like. Applied to the baseline copy independently: the project's file may carry the user's own edits.
        list(merge_marker_blocks(target / BASELINE_REL, by_key, strict=False))  # generator: must be consumed
    for file_rel, new_text in merge_marker_blocks(target, by_key, strict=True, dry_run=dry_run):
        touched.append(file_rel)
        if not dry_run:
            files = state.setdefault("files", {})
            if file_rel in files:
                files[file_rel]["sha"] = sha(new_text.encode())
    return touched


def merge_marker_blocks(root: Path, by_key: dict, strict: bool, dry_run: bool = False):
    """Unique-merges lines into marker blocks under `root`; yields (file_rel, new_text) for every file changed."""
    for (file_rel, marker), lines in by_key.items():
        path = root / file_rel
        if not path.exists():
            if strict and file_rel in STRICT_MARKER_FILES:
                raise SystemExit(f"error: {file_rel} missing — cannot insert into marker block '{marker}'")
            continue
        text = path.read_text()
        pattern = re.compile(r"(?P<indent>[ \t]*)(?P<c>//|#|<!--) <arch-spec:" + re.escape(marker) + r">.*?\n(?P<body>.*?)(?P=indent)(?P=c) </arch-spec:" + re.escape(marker) + r">", re.S)
        m = pattern.search(text)
        if not m:
            if not strict:
                continue
            raise SystemExit(f"error: marker block <arch-spec:{marker}> not found in {file_rel} — restore the "
                             f"`<arch-spec:{marker}>` / `</arch-spec:{marker}>` comment lines and re-run")
        indent = m.group("indent")
        existing = [l.strip() for l in m.group("body").splitlines() if l.strip()]
        merged = existing + [l for l in lines if l.strip() not in existing]
        if merged == existing:
            continue
        body = "".join(f"{indent}{l}\n" for l in merged)
        start, end = m.span("body")
        new_text = text[:start] + body + text[end:]
        if not dry_run:
            path.write_text(new_text)
        yield file_rel, new_text


def component_inserts(comp: dict, values: dict) -> list:
    inserts = []
    for inc in comp.get("settings_includes") or []:
        inserts.append(("settings.gradle.kts", "includes", [f'include("{inc}")']))
    for dep in comp.get("app_dependencies") or []:
        inserts.append(("app/build.gradle.kts", "app-dependencies", [dep]))
    for ins in comp.get("inserts") or []:
        file_rel = substitute(ins["file"], values, "components.yml inserts")
        lines = [substitute(l, values, "components.yml inserts") for l in (ins.get("lines") or [])]
        inserts.append((file_rel, ins["marker"], lines))
    return inserts


def ordered_components(state: dict) -> list:
    """Installed components in install order (older state files without `install_order` fall back to manifest order)."""
    installed = state.get("components", {})
    order = [c for c in state.get("install_order", []) if c in installed]
    return order + [c for c in load_manifest() if c in installed and c not in order]


def cmd_install(target: Path, name: str, fresh: bool, dry_run: bool) -> int:
    manifest = load_manifest()
    if name not in manifest:
        raise SystemExit(f"error: unknown component '{name}'. Known: {', '.join(manifest)}")
    comp = manifest[name]
    state = load_state(target)
    installed = state.setdefault("components", {})
    missing = [d for d in comp.get("depends_on") or [] if d not in installed]
    if missing:
        raise SystemExit(f"error: '{name}' depends on {missing} — install those first")
    values = resolve_values(state.get("answers", {}))
    first_time = name not in installed
    fresh_replaces = {substitute(r, values, "replaces") for r in (comp.get("replaces") or [])} if (fresh or first_time) else set()

    planned = []
    for src, rel in iter_payload(comp["payload"], exclude_feature_template=True):
        dest_rel, data = render_file(src, rel, values)
        planned.append((dest_rel, data, f"{comp['payload']}/{rel}"))

    report = write_planned(target, state, planned, fresh_replaces, dry_run)

    if first_time or fresh:
        # Stock files live under the namespace the empty project was created with, which may differ from the
        # APP_PACKAGE the user chose — `removes` globs use {{STOCK_APP_PACKAGE_PATH}} for them.
        stock_values = dict(values, STOCK_APP_PACKAGE_PATH=(state.get("stock", {}).get("app_package")
                                                            or values["APP_PACKAGE"]).replace(".", "/"))
        just_written = {d for d, _, _ in planned}
        for pattern in comp.get("removes") or []:
            pat = substitute(pattern, stock_values, "removes")
            for p in sorted(target.glob(pat)):
                if p.relative_to(target).as_posix() in just_written:
                    continue  # same path as a file this component just rendered — never delete our own output
                if p.is_file():
                    report.setdefault("removed", []).append(p.relative_to(target).as_posix())
                    if not dry_run:
                        p.unlink()
                        # Drop directories the removal left empty (e.g. the stock ui/theme package).
                        d = p.parent
                        while d != target and d.is_dir() and not any(d.iterdir()):
                            d.rmdir()
                            d = d.parent

    if name == "architecture":
        feat_report = scaffold(target, state, "home", values=None, dry_run=dry_run, register=True)
        for k in ("written", "unchanged", "conflicts"):
            report[k] += feat_report[k]
        if not dry_run and "home" not in state.setdefault("features", []):
            state["features"].append("home")

    all_inserts = []
    for other in ordered_components(state) + ([name] if first_time else []):
        all_inserts += component_inserts(manifest[other], values)
    report["markers"] = apply_markers(target, state, all_inserts, dry_run)

    if not dry_run:
        installed[name] = {"spec_version": spec_version(), "tier": comp["tier"]}
        order = state.setdefault("install_order", [])
        if name not in order:
            order.append(name)
        state["deferred"] = compute_deferred(state.get("answers", {}), installed)
        save_state(target, state)
    print(json.dumps({"component": name, "dry_run": dry_run, **report}, indent=2))
    return 2 if report["conflicts"] else 0


def scaffold(target: Path, state: dict, feature: str, values, dry_run: bool, register: bool,
             baseline: bool = True) -> dict:
    if not re.match(VALIDATORS["FEATURE"], feature):
        raise SystemExit(f"error: feature name '{feature}' must match {VALIDATORS['FEATURE']}")
    values = resolve_values(state.get("answers", {}), feature=feature)
    root = PAYLOAD / FEATURE_TEMPLATE_REL
    planned = []
    for src in sorted(root.rglob("*")):
        if src.is_dir() or src.name == ".DS_Store":
            continue
        rel = src.relative_to(root).as_posix()
        dest_rel, data = render_file(src, rel, values)
        planned.append((dest_rel, data, f"{FEATURE_TEMPLATE_REL}/{rel}"))
    report = write_planned(target, state, planned, set(), dry_run, baseline=baseline)
    if register:
        inserts = [
            ("settings.gradle.kts", "includes",
             [f'include(":{feature}-ui-api")', f'include(":{feature}-ui")', f'include(":{feature}")']),
            # The host depends on every feature's contract (routes for start destination / sibling seams) and impl.
            ("app/build.gradle.kts", "app-dependencies",
             [f'implementation(project(":{feature}-ui-api"))', f'implementation(project(":{feature}-ui"))']),
        ]
        app_kt = f'app/src/main/java/{values["APP_PACKAGE_PATH"]}/App.kt'
        if (target / app_kt).exists() or any(p[0] == app_kt for p in planned):
            inserts.append((app_kt, "feature-registrations",
                            [f'{values["ROOT_PACKAGE"]}.{values["FEATURE_PKG"]}.ui.{values["FEATURE_PASCAL"]}FeatureImpl().addToRegistry()']))
        report["markers"] = apply_markers(target, state, inserts, dry_run, baseline=baseline)
    return report


def cmd_scaffold(target: Path, feature: str, dry_run: bool) -> int:
    state = load_state(target)
    if "architecture" not in state.get("components", {}):
        raise SystemExit("error: install the 'architecture' component first")
    report = scaffold(target, state, feature, values=None, dry_run=dry_run, register=True)
    if not dry_run:
        state.setdefault("features", [])
        if feature not in state["features"]:
            state["features"].append(feature)
        save_state(target, state)
    print(json.dumps({"feature": feature, "dry_run": dry_run, **report}, indent=2))
    return 2 if report["conflicts"] else 0


def cmd_rerender(target: Path, keys: list) -> int:
    """Re-renders files whose payload source uses any of `keys`, if they were not modified since install."""
    state = load_state(target)
    manifest = load_manifest()
    keys = list(keys) + [d for k in keys for d in DERIVED_FROM.get(k, [])]
    values_by_feature = {}
    planned = []
    for dest_rel, meta in sorted(state.get("files", {}).items()):
        source = meta.get("source", "")
        src = PAYLOAD / source
        if not src.exists() or src.suffix.lower() in BINARY_EXT:
            continue
        used = set(PLACEHOLDER.findall(src.read_text()))
        if not used & set(keys):
            continue
        feature = None
        if source.startswith(FEATURE_TEMPLATE_REL + "/"):
            feature = next((f for f in state.get("features", ["home"]) + ["home"] if f"/{f.replace('-', '')}/" in dest_rel or f"{f}-ui" in dest_rel or dest_rel.startswith(f + "/")), "home")
        values = values_by_feature.setdefault(feature, resolve_values(state.get("answers", {}), feature=feature))
        data = substitute(src.read_text(), values, source).encode()
        planned.append((dest_rel, data, source))
    report = write_planned(target, state, planned, set(), dry_run=False)
    state["deferred"] = compute_deferred(state.get("answers", {}), state.get("components", {}))
    save_state(target, state)
    print(json.dumps({"rerender": keys, **report}, indent=2))
    _ = manifest
    return 2 if report["conflicts"] else 0


def cmd_snapshot(target: Path, out: Path) -> int:
    """Renders what THIS spec would produce for the project's recorded answers, components (in install order),
    features and relocations into an empty directory `out` — "theirs" for an upgrade. Touches nothing in target."""
    state = load_state(target)
    if out.exists() and any(out.iterdir()):
        raise SystemExit(f"error: {out} must be empty or absent")
    out.mkdir(parents=True, exist_ok=True)
    manifest = load_manifest()
    shadow = {"answers": state.get("answers", {}), "stock": state.get("stock", {}),
              "relocations": state.get("relocations", []), "files": {}}
    values = resolve_values(shadow["answers"])
    rendered, inserts = [], []
    for name in ordered_components(state):
        comp = manifest.get(name)
        if not comp:
            print(f"note: component '{name}' no longer exists in this spec version — skipped", file=sys.stderr)
            continue
        planned = [(*render_file(src, rel, values), f"{comp['payload']}/{rel}")
                   for src, rel in iter_payload(comp["payload"], exclude_feature_template=True)]
        rendered += write_planned(out, shadow, planned, set(), dry_run=False, baseline=False)["written"]
        if name == "architecture":  # `install architecture` scaffolds the reference feature before its inserts
            rendered += scaffold(out, shadow, "home", values=None, dry_run=False, register=True,
                                 baseline=False)["written"]
        # Same sequence as `install`: after each component, the inserts of every component so far are re-applied,
        # so marker lines land in the same order as in the project.
        inserts += component_inserts(comp, values)
        apply_markers(out, shadow, inserts, dry_run=False, baseline=False)
    for feature in [f for f in state.get("features", []) if f != "home"]:  # features scaffolded after install
        rendered += scaffold(out, shadow, feature, values=None, dry_run=False, register=True,
                             baseline=False)["written"]
    print(json.dumps({"snapshot": str(out), "spec_version": spec_version(), "files": len(rendered)}, indent=2))
    return 0


def cmd_relocate(target: Path, old: str, new: str) -> int:
    """Records that the project moved a rendered top-level module directory (`connectivity` → `netstate`): re-keys the
    checksums and the baseline, and makes every later install / rerender / snapshot / marker insert use the new
    name. Run it right after renaming the module (git mv + settings/app references); it moves no project file."""
    state = load_state(target)
    old, new = old.strip("/"), new.strip("/")
    for v in (old, new):
        if not re.match(r"^[a-z][a-z0-9-]*$", v):
            raise SystemExit(f"error: '{v}' is not a top-level module directory name")
    if not (target / new).is_dir():
        raise SystemExit(f"error: {new}/ does not exist — rename the module first (git mv {old} {new})")
    files = state.setdefault("files", {})
    moved = 0
    for rel in sorted(list(files)):
        if rel.startswith(old + "/"):
            files[new + rel[len(old):]] = files.pop(rel)
            moved += 1
    base_old, base_new = target / BASELINE_REL / old, target / BASELINE_REL / new
    if base_old.is_dir() and not base_new.exists():
        base_old.rename(base_new)
    rel_list = [r for r in state.setdefault("relocations", []) if r["from"] != old + "/"]
    for r in rel_list:                     # chain: a → b, then b → c  ⇒  a → c
        if r["to"] == old + "/":
            r["to"] = new + "/"
    rel_list.append({"from": old + "/", "to": new + "/"})
    state["relocations"] = rel_list
    save_state(target, state)
    print(json.dumps({"relocated": f"{old}/ -> {new}/", "files_rekeyed": moved, "relocations": rel_list}, indent=2))
    return 0


def cmd_rebase(target: Path, snapshot: Path) -> int:
    """Finishes an upgrade: after the project's files were merged with `snapshot` (the new spec's rendering), the
    snapshot becomes the new baseline, every snapshot file's checksum is re-recorded from the project's CURRENT
    file (so the user's kept edits count as theirs, not as conflicts, next time), files the new spec no longer
    renders leave the bookkeeping, and the installed version becomes this spec's version."""
    state = load_state(target)
    if not snapshot.is_dir():
        raise SystemExit(f"error: snapshot {snapshot} not found — run `snapshot --out DIR` first")
    pending = [p.relative_to(target).as_posix() for p in target.rglob("*.arch-spec-new") if ".git" not in p.parts]
    if pending:
        raise SystemExit(f"error: resolve conflicts first: {pending}")
    base = target / BASELINE_REL
    if base.exists():
        import shutil
        shutil.rmtree(base)
    files = {}
    old = state.get("files", {})
    for f in sorted(snapshot.rglob("*")):
        if f.is_dir():
            continue
        rel = f.relative_to(snapshot).as_posix()
        write_baseline(target, rel, f.read_bytes())
        cur = target / rel
        source = old.get(rel, {}).get("source", "")
        if cur.exists():
            files[rel] = {"sha": sha(cur.read_bytes()), "source": source}
    dropped = sorted(set(old) - set(files))
    state["files"] = files
    state["spec_version"] = spec_version()
    for c in state.get("components", {}).values():
        c["spec_version"] = spec_version()
    state["deferred"] = compute_deferred(state.get("answers", {}), state.get("components", {}))
    save_state(target, state)
    print(json.dumps({"rebased_to": spec_version(), "files": len(files), "no_longer_rendered": dropped}, indent=2))
    return 0


def effective_gates(state: dict) -> dict:
    manifest = load_manifest()
    return {c: [relocate_line(state, " " + g)[1:] for g in (manifest.get(c, {}).get("gate") or [])]
            for c in ordered_components(state)}


def cmd_init(target: Path) -> int:
    p = state_path(target)
    if p.exists():
        print(f"{p} already exists — leaving it untouched")
        return 0
    answers = {}
    stock_namespace = None
    # Derive what we can from the stock empty project.
    app_gradle = target / "app" / "build.gradle.kts"
    if app_gradle.exists():
        t = app_gradle.read_text()
        if m := re.search(r'applicationId\s*=\s*"([^"]+)"', t):
            answers["APPLICATION_ID"] = m.group(1)
        if m := re.search(r'namespace\s*=\s*"([^"]+)"', t):
            stock_namespace = m.group(1)  # recorded as stock only: APP_PACKAGE keeps following APPLICATION_ID
        if m := re.search(r"minSdk\s*=\s*(\d+)", t):
            answers["MIN_SDK"] = m.group(1)
    strings = target / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    if strings.exists() and (m := re.search(r'<string name="app_name">([^<]+)</string>', strings.read_text())):
        answers["APP_NAME"] = m.group(1)
    # PROJECT_SLUG is not recorded: it follows APP_NAME until answered (the stock rootProject.name is just "My App").
    state = {"spec_version": spec_version(), "answers": answers, "components": {}, "features": [], "files": {},
             "stock": {"app_package": stock_namespace}}
    state["deferred"] = compute_deferred(answers, ())
    save_state(target, state)
    print(json.dumps({"created": str(p), "derived_answers": answers, "resolved": resolve_values(answers)}, indent=2))
    return 0


def cmd_set(target: Path, pairs: list) -> int:
    state = load_state(target)
    answers = state.setdefault("answers", {})
    errors = []
    for pair in pairs:
        key, sep, value = pair.partition("=")
        if not sep or key not in DEFAULTS:
            errors.append(f"unknown parameter or bad syntax: {pair!r}")
            continue
        if value == "-":
            answers[key] = "-"
            continue
        rule = VALIDATORS.get(key)
        if rule and not re.match(rule, value):
            errors.append(f"{key}={value!r} does not match {rule}")
            continue
        answers[key] = value
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    state["deferred"] = compute_deferred(answers, state.get("components", {}))
    save_state(target, state)
    print(json.dumps({"answers": answers, "deferred": state["deferred"]}, indent=2))
    return 0


def cmd_status(target: Path) -> int:
    state = load_state(target)
    modified = []
    for rel, meta in sorted(state.get("files", {}).items()):
        p = target / rel
        if not p.exists():
            modified.append(f"{rel} (deleted)")
        elif sha(p.read_bytes()) != meta["sha"]:
            modified.append(rel)
    pending_new = [p.relative_to(target).as_posix() for p in target.rglob("*.arch-spec-new")]
    print(json.dumps({
        "spec_version_installed": state.get("spec_version"),
        "spec_version_available": spec_version(),
        "components": state.get("components", {}),
        "features": state.get("features", []),
        "install_order": ordered_components(state),
        "relocations": state.get("relocations", []),
        "gates": effective_gates(state),
        "deferred": state.get("deferred", {}),
        "modified_since_install": modified,
        "unresolved_conflicts": pending_new,
    }, indent=2))
    return 0


# --------------------------------------------------------------------------------------------- self-check
def cmd_self_check() -> int:
    problems = []
    manifest = load_manifest()
    documented = set(re.findall(r"`([A-Z][A-Z0-9_]+)`", PARAMS_DOC.read_text()))
    known = set(DEFAULTS) | DERIVED
    for name, comp in manifest.items():
        for field in ("tier", "payload", "doc"):
            if not comp.get(field):
                problems.append(f"{name}: missing '{field}'")
        if not (PAYLOAD / (comp.get("payload") or "")).is_dir():
            problems.append(f"{name}: payload/{comp.get('payload')} missing")
        if comp.get("doc") and not (SPEC_ROOT / comp["doc"]).exists():
            problems.append(f"{name}: doc {comp['doc']} missing")
        for d in comp.get("depends_on") or []:
            if d not in manifest:
                problems.append(f"{name}: depends on unknown '{d}'")
    for path in sorted(PAYLOAD.rglob("*")):
        if path.is_dir():
            continue
        names = set(PLACEHOLDER.findall(path.relative_to(PAYLOAD).as_posix()))
        if path.suffix.lower() not in BINARY_EXT:
            try:
                names |= set(PLACEHOLDER.findall(path.read_text()))
            except UnicodeDecodeError:
                problems.append(f"{path.relative_to(SPEC_ROOT)}: not UTF-8 and not a known binary type")
        for n in names - known:
            problems.append(f"{path.relative_to(SPEC_ROOT)}: undefined placeholder {{{{{n}}}}}")
        for n in names - documented:
            if n in known:
                problems.append(f"{path.relative_to(SPEC_ROOT)}: placeholder {n} not documented in parameters.md")
    for n in DEFAULTS:
        if n not in documented:
            problems.append(f"parameters.md does not document {n}")
    if problems:
        print("\n".join(sorted(set(problems))))
        return 1
    print(f"self-check OK: {len(manifest)} components, payload placeholders all defined and documented")
    return 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--self-check", action="store_true")
    # Accepted before OR after the subcommand (INSTALL.md's shorthand `render.py --target . install X`).
    ap.add_argument("--target", type=Path, dest="target_global")
    sub = ap.add_subparsers(dest="cmd")
    for c in ("init", "set", "plan", "install", "scaffold-feature", "rerender", "status", "snapshot", "relocate",
              "rebase"):
        p = sub.add_parser(c)
        p.add_argument("--target", type=Path, dest="target_sub")
        if c in ("plan", "install"):
            p.add_argument("component")
        if c == "install":
            p.add_argument("--fresh", action="store_true")
        if c == "scaffold-feature":
            p.add_argument("name")
            p.add_argument("--dry-run", action="store_true")
        if c in ("set", "rerender"):
            p.add_argument("items", nargs="+")
        if c == "snapshot":
            p.add_argument("--out", required=True, type=Path)
        if c == "rebase":
            p.add_argument("--snapshot", required=True, type=Path)
        if c == "relocate":
            p.add_argument("old")
            p.add_argument("new")
    a = ap.parse_args(argv)
    if a.self_check:
        return cmd_self_check()
    if not a.cmd:
        ap.print_help()
        return 1
    target_arg = a.target_sub or a.target_global
    if target_arg is None:
        ap.error("--target DIR is required")
    target = target_arg.resolve()
    if a.cmd == "init":
        return cmd_init(target)
    if a.cmd == "set":
        return cmd_set(target, a.items)
    if a.cmd == "plan":
        return cmd_install(target, a.component, fresh=False, dry_run=True)
    if a.cmd == "install":
        return cmd_install(target, a.component, fresh=a.fresh, dry_run=False)
    if a.cmd == "scaffold-feature":
        return cmd_scaffold(target, a.name, a.dry_run)
    if a.cmd == "rerender":
        return cmd_rerender(target, a.items)
    if a.cmd == "status":
        return cmd_status(target)
    if a.cmd == "snapshot":
        return cmd_snapshot(target, a.out.resolve())
    if a.cmd == "relocate":
        return cmd_relocate(target, a.old, a.new)
    if a.cmd == "rebase":
        return cmd_rebase(target, a.snapshot.resolve())
    return 1


if __name__ == "__main__":
    sys.exit(main())
