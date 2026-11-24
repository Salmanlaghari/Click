#!/usr/bin/env python3
import json, sys, urllib.request, urllib.error
sys.path.insert(0, "/opt/muse/skills/custom-connector-setup/bin")
from dynamic_credentials import add_surrogate_to_request, read_json_response
API = "https://api.github.com"; OWNER, REPO = "Salmanlaghari", "Click"
AUTHOR = {"name": "Salman Laghari", "email": "salmanlaghari@users.noreply.github.com"}
def api(method, path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(API + path, data=data, method=method, headers={"Accept": "application/vnd.github+json", "Content-Type": "application/json", "User-Agent": "muse-github-skill"})
    add_surrogate_to_request(req, "custom.github", allowed_hosts=["api.github.com"])
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return read_json_response(resp), None
    except urllib.error.HTTPError as e:
        return None, (e.code, e.read().decode("utf-8", "replace")[:800])
branch, tree_sha, extra_parent, message = sys.argv[1:5]
ref, err = api("GET", f"/repos/{OWNER}/{REPO}/git/ref/heads/{branch}")
if err: print(f"GET ref failed: {err}", file=sys.stderr); sys.exit(1)
tip = ref["object"]["sha"]
commit, err = api("POST", f"/repos/{OWNER}/{REPO}/git/commits", {"message": message, "tree": tree_sha, "parents": [tip, extra_parent], "author": AUTHOR, "committer": AUTHOR})
if err: print(f"POST commit failed: {err}", file=sys.stderr); sys.exit(1)
new_sha = commit["sha"]
upd, err = api("PATCH", f"/repos/{OWNER}/{REPO}/git/refs/heads/{branch}", {"sha": new_sha, "force": False})
if err: print(f"PATCH ref failed: {err}", file=sys.stderr); sys.exit(1)
print(f"branch {branch}: {tip[:8]} -> merge commit {new_sha}")
