#!/usr/bin/env python3
"""Figma URL 의 노드를 PNG 파일로 내려받는다 — 시안 참조 패널(refrow)용.

Figma MCP 의 get_screenshot 은 이미지를 대화에 띄울 뿐 파일로 주지 않는다.
합성에는 파일이 필요하므로 REST API 로 렌더 URL 을 받아 저장한다.

토큰: FIGMA_ACCESS_TOKEN (없으면 FIGMA_TOKEN)
      Figma > Settings > Personal access tokens — 권한에 file_content:read 포함.

usage: fetch_figma.py --out <dir> [--scale 2] [--names a,b] URL [URL...]
       URL 은 node-id 쿼리가 있는 figma.com 링크:
       https://www.figma.com/design/<file_key>/<이름>?node-id=123-456

출력: <out>/figma-{이름 또는 node-id}.png
"""
import argparse
import json
import os
import pathlib
import re
import sys
import urllib.parse
import urllib.request

API = "https://api.figma.com/v1/images/{file_key}?ids={ids}&format=png&scale={scale}"


def get(url, token=None):
    req = urllib.request.Request(url)
    if token:
        req.add_header("X-Figma-Token", token)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def parse_figma_url(url):
    m = re.search(r"figma\.com/(?:design|file|proto|board)/([A-Za-z0-9]+)", url)
    if not m:
        sys.exit(f"ERROR: file_key 를 못 찾음: {url}")
    qs = urllib.parse.parse_qs(urllib.parse.urlparse(url).query)
    node = (qs.get("node-id") or [None])[0]
    if not node:
        sys.exit(f"ERROR: node-id 쿼리가 없음: {url}")
    return m.group(1), node.replace(":", "-")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("urls", nargs="+")
    ap.add_argument("--out", required=True)
    ap.add_argument("--scale", default="2")
    ap.add_argument("--names", help="출력 이름 (쉼표 구분, URL 개수와 일치) — figma-{이름}.png")
    a = ap.parse_args()

    token = os.environ.get("FIGMA_ACCESS_TOKEN") or os.environ.get("FIGMA_TOKEN")
    if not token:
        sys.exit("ERROR: FIGMA_ACCESS_TOKEN 미설정 — 시안 패널을 생략하고 진행할 것")

    names = [s.strip() for s in a.names.split(",")] if a.names else None
    if names and len(names) != len(a.urls):
        sys.exit(f"ERROR: --names {len(names)}개 vs URL {len(a.urls)}개 — 개수가 맞아야 한다")

    targets = []   # (file_key, node, 출력 이름)
    for i, url in enumerate(a.urls):
        key, node = parse_figma_url(url)
        targets.append((key, node, names[i] if names else node))

    out = pathlib.Path(a.out)
    out.mkdir(parents=True, exist_ok=True)

    # file_key 별로 묶어 한 번씩만 호출한다 (노드마다 호출하면 rate limit 에 걸린다)
    by_key = {}
    for key, node, name in targets:
        by_key.setdefault(key, []).append((node, name))

    fails = []
    for key, nodes in by_key.items():
        ids = ",".join(n for n, _ in nodes)
        resp = json.loads(get(API.format(file_key=key, ids=ids, scale=a.scale), token))
        if resp.get("err"):
            sys.exit(f"ERROR: Figma API — {resp['err']}")
        images = resp.get("images") or {}
        for node, name in nodes:
            render_url = images.get(node.replace("-", ":"))
            if not render_url:
                fails.append(node)
                print(f"MISS  {name} ({node}) — 렌더 URL 없음", file=sys.stderr)
                continue
            dst = out / f"figma-{name}.png"
            dst.write_bytes(get(render_url))
            print(f"OK    {dst}  ({node}, {dst.stat().st_size} bytes)")

    if fails:
        sys.exit(f"ERROR: {len(fails)}개 노드 렌더 실패 — node-id 가 그 file_key 에 있는지 확인: "
                 + ", ".join(fails))


if __name__ == "__main__":
    main()
