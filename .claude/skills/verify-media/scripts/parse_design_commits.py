#!/usr/bin/env python3
"""검수 커밋에서 Figma 노드와 변경 파일을 뽑는다 — 디자인 검수 모드(Phase 0)의 입력.

`/figma-apply` 는 커밋 메시지에 아래 형태로 근거를 남긴다 — 이 규약이 입력이다.

    피그마: https://www.figma.com/design/<file_key>/?node-id=37271-18531 (AlbumFeedview)
    디자이너 프롬프트: ... /figma-apply https://www.figma.com/design/<file_key>/?node-id=38804-166394

노드를 사람이 다시 찾지 않게 하는 것이 목적이다. 커밋 감으로 훑지 말고 이 스크립트로
전수 파싱한다 — 커밋이 많거나 본문이 길면 눈으로는 놓친다.

출력의 노드별 `url` 은 그대로 `fetch_figma.py` 에 넘길 수 있다.

usage: parse_design_commits.py --base <ref> [--head HEAD] [--json]
"""
import argparse
import json
import re
import subprocess
import sys

NODE_RE = re.compile(
    r"figma\.com/(?:design|file|proto)/([A-Za-z0-9]+)[^\s)]*?node-id=([0-9]+[-:%3A]+[0-9]+)",
    re.IGNORECASE)
NAME_RE = re.compile(r"node-id=[0-9]+[-:%3A]+[0-9]+[^\s)]*\s*\(([^)]+)\)")


def norm_node(raw):
    return re.sub(r"(?:%3A|:)", "-", raw, flags=re.IGNORECASE)


def git(*args):
    r = subprocess.run(["git", *args], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(f"ERROR: git {' '.join(args)}\n{r.stderr.strip()}")
    return r.stdout


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True, help="비교 기준 ref (보통 PR base)")
    ap.add_argument("--head", default="HEAD", help="검수 브랜치 ref (기본 HEAD)")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()

    sep = "\x1e"
    raw = git("log", f"--format=%H{sep}%s{sep}%b\x1d", f"{a.base}..{a.head}")

    commits = []
    for chunk in raw.split("\x1d"):
        chunk = chunk.strip("\n")
        if not chunk.strip():
            continue
        sha, subject, body = (chunk.split(sep) + ["", ""])[:3]
        nodes = []
        for fk, node_raw in NODE_RE.findall(body + " " + subject):
            node = norm_node(node_raw)
            if node not in [n["node"] for n in nodes]:
                nodes.append({
                    "file_key": fk, "node": node,
                    "url": f"https://www.figma.com/design/{fk}/?node-id={node}",
                })
        names = NAME_RE.findall(body)
        for i, n in enumerate(nodes):
            n["name"] = names[i] if i < len(names) else ""
        files = git("show", "--name-only", "--format=", sha).split()
        commits.append({"sha": sha[:10], "subject": subject,
                        "nodes": nodes, "files": files})

    file_keys = {n["file_key"] for c in commits for n in c["nodes"]}
    all_nodes = {}
    for c in commits:
        for n in c["nodes"]:
            all_nodes.setdefault(n["node"], n)

    if a.json:
        print(json.dumps({"commits": commits,
                          "file_keys": sorted(file_keys),
                          "nodes": list(all_nodes.values())},
                         ensure_ascii=False, indent=2))
        return

    print(f"검수 커밋 {len(commits)}개 (base={a.base})\n")
    for c in commits:
        print(f"  {c['sha']}  {c['subject']}")
        for n in c["nodes"]:
            print(f"      node {n['node']}  {n['name']}")
        for f in c["files"]:
            print(f"      · {f}")
        print()

    if len(file_keys) > 1:
        print(f"WARN: Figma file_key 가 {len(file_keys)}개다 — 노드가 어느 파일 것인지 섞지 말 것")
    print(f"file_key: {', '.join(sorted(file_keys)) or '없음'}")
    print(f"노드 {len(all_nodes)}개: {', '.join(sorted(all_nodes)) or '없음'}")
    if not all_nodes:
        print("\nNOTE: 커밋 메시지에 Figma URL 이 없다. /figma-apply 로 만든 커밋이 아니거나\n"
              "      규약이 지켜지지 않았다 — 이때만 사용자에게 node URL 을 묻는다.")


if __name__ == "__main__":
    main()
