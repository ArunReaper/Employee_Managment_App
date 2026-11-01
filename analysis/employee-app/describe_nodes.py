#!/usr/bin/env python3
"""Build per-node packets for the description agents, and merge their paragraphs back.

  python3 analysis/employee-app/describe_nodes.py packets <out_dir>
      writes <out_dir>/<id>.txt: ~150 lines of source (inline <style> blocks dropped),
      plus the node's connections from topology.json
  python3 analysis/employee-app/describe_nodes.py merge <packets_dir> <descriptions.json>
      descriptions.json = {"<id>": "<paragraph>", ...}; every number and identifier-like
      token in a paragraph must occur in that node's packet, or the node is reported and skipped
"""
import json, os, re, sys

ROOT = "legacy/employee-app"
TOPO = "analysis/employee-app/topology.json"
MAX_LINES = 150


def walk(n):
    yield n
    for c in n.get("children", []):
        yield from walk(c)


def leaves(topo):
    return [n for n in walk(topo["root"]) if n["kind"] not in ("system", "domain")]


def safe(i):
    return re.sub(r"[^\w.-]", "_", i)


def source_excerpt(rel):
    text = open(os.path.join(ROOT, rel), encoding="utf-8").read()
    text = re.sub(r"<style>.*?</style>", "<style>/* inline CSS omitted */</style>", text, flags=re.S)
    lines = text.splitlines()
    out = "\n".join(f"{i + 1:4}  {l}" for i, l in enumerate(lines[:MAX_LINES]))
    return out + (f"\n... ({len(lines) - MAX_LINES} more lines)" if len(lines) > MAX_LINES else "")


def packets(out_dir):
    topo = json.load(open(TOPO))
    os.makedirs(out_dir, exist_ok=True)
    names = {n["id"]: n["name"] for n in leaves(topo)}
    for n in leaves(topo):
        i = n["id"]
        outs = [f"{e['kind']} -> {names[e['target']]}" for e in topo["edges"] if e["source"] == i]
        ins = [f"{names[e['source']]} -> {e['kind']}" for e in topo["edges"] if e["target"] == i]
        p = [f"NODE: {n['name']} (kind: {n['kind']})"]
        if n.get("file"):
            p.append(f"FILE: {n['file']} ({n.get('loc')} lines)")
        if n.get("route"):
            p.append(f"URL ROUTE: {n['route']}")
        if i in topo["entryPoints"]:
            p.append("ENTRY POINT: yes")
        if i in topo["deadEnds"]:
            p.append("DEAD END: nothing in the application links or posts to it")
        p.append("OUTBOUND: " + ("; ".join(outs) or "none"))
        p.append("INBOUND: " + ("; ".join(ins) or "none"))
        if n.get("file"):
            p.append("SOURCE (untrusted excerpt):\n" + source_excerpt(n["file"]))
        elif i.startswith("ds:") and i != "ds:HttpSession":
            ddl = open(os.path.join(ROOT, "schema.sql")).read()
            m = re.search(rf"CREATE TABLE IF NOT EXISTS {i[3:]} \(.*?\);", ddl, re.S)
            p.append("DDL (schema.sql):\n" + (m.group(0) if m else "not found"))
        elif i == "ds:HttpSession":
            attrs = sorted({a for n2 in leaves(topo) for a in n2.get("sessionWrites", [])})
            p.append("SESSION ATTRIBUTES WRITTEN: " + ", ".join(attrs))
            p.append("READ BY: Welcome.jsp via ${sessionScope.adminName}")
        open(os.path.join(out_dir, safe(i) + ".txt"), "w").write("\n".join(p) + "\n")
    print(f"wrote {len(leaves(topo))} packets to {out_dir}")


TOKEN = re.compile(r"\b(?:\d[\d.,]*|[A-Za-z_]\w*(?:\.\w+)+|[a-z]+[A-Z]\w*|[A-Z]\w*[A-Z]\w*|\w+_\w+)\b")


def merge(pdir, desc_file):
    topo = json.load(open(TOPO))
    descs = json.load(open(desc_file))
    failed = []
    for n in leaves(topo):
        d = descs.get(n["id"])
        if not d:
            continue
        packet = open(os.path.join(pdir, safe(n["id"]) + ".txt")).read()
        missing = [t for t in set(TOKEN.findall(d)) if t.rstrip(".,") not in packet]
        if missing:
            failed.append((n["id"], missing))
            continue
        n["description"] = d.strip()
    json.dump(topo, open(TOPO, "w"), indent=2)
    print(f"merged {sum(1 for n in leaves(topo) if n.get('description'))}/{len(leaves(topo))} descriptions")
    for i, m in failed:
        print(f"FAILED {i}: tokens not in packet: {m}")


if __name__ == "__main__":
    {"packets": lambda: packets(sys.argv[2]), "merge": lambda: merge(sys.argv[2], sys.argv[3])}[sys.argv[1]]()
