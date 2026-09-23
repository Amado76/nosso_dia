#!/usr/bin/env python3
"""Generate a dependency-free interactive graph from local Markdown links."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "knowledge-graph.html"
LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")


def main() -> None:
    markdown_files = [ROOT / "README.md", ROOT / "AGENTS.md", *sorted((ROOT / "docs").glob("*.md"))]
    nodes = []
    edges = set()
    known = {path.resolve() for path in markdown_files}

    for path in markdown_files:
        rel = path.relative_to(ROOT).as_posix()
        nodes.append({"id": rel, "label": path.stem if path.name != "README.md" else "README", "path": rel})
        content = path.read_text(encoding="utf-8")
        for raw_target in LINK.findall(content):
            target = raw_target.split("#", 1)[0].strip()
            if not target or ":" in target:
                continue
            resolved = (path.parent / target).resolve()
            if resolved in known and resolved != path.resolve():
                edges.add((rel, resolved.relative_to(ROOT).as_posix()))

    payload = json.dumps({"nodes": nodes, "edges": sorted(map(list, edges))}, ensure_ascii=False)
    payload = payload.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
    template = (ROOT / "scripts" / "doc-graph-template.html").read_text(encoding="utf-8")
    OUTPUT.write_text(template.replace("__GRAPH_DATA__", payload), encoding="utf-8")
    print(f"Generated {OUTPUT.relative_to(ROOT)} with {len(nodes)} documents and {len(edges)} links")


if __name__ == "__main__":
    main()
