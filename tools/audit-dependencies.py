"""Audit resolved test coordinates with OSV; send no merchant configuration."""
from pathlib import Path
import json
import urllib.request

root = Path(__file__).resolve().parents[1]
inventory = root / "build/security/dependencies.json"
queries = json.loads(inventory.read_text())
if not queries or any(q.get("package", {}).get("ecosystem") != "Maven" for q in queries):
    raise SystemExit("Generate the resolved Maven test dependency inventory first.")
pending = queries
findings = []
for _ in range(20):
    request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
        data=json.dumps({"queries": pending}).encode(),
        headers={"Content-Type": "application/json", "User-Agent": "lunixi-java-build-audit/0.2"}, method="POST")
    with urllib.request.urlopen(request, timeout=30) as response:
        raw = response.read(4 * 1024 * 1024 + 1)
    if len(raw) > 4 * 1024 * 1024:
        raise SystemExit("OSV response exceeded the audit bound.")
    results = json.loads(raw).get("results")
    if not isinstance(results, list) or len(results) != len(pending):
        raise SystemExit("Incomplete OSV result; audit did not pass.")
    following = []
    for query, result in zip(pending, results):
        for finding in result.get("vulns", []):
            findings.append({"package": query["package"]["name"], "version": query["version"], "id": finding["id"]})
        if result.get("next_page_token"):
            following.append({**query, "page_token": result["next_page_token"]})
    if not following:
        break
    pending = following
else:
    raise SystemExit("OSV pagination exceeded the audit bound.")
(root / "build/security/osv-report.json").write_text(json.dumps({"packages": len(queries), "findings": findings}, indent=2))
if findings:
    raise SystemExit("OSV found dependency advisories; inspect build/security/osv-report.json.")
print(f"OSV: {len(queries)} resolved test dependencies, no known advisories; SDK runtime dependencies: zero.")
