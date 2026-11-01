"""Scan a clean source export, and prove an added fixture credential is rejected."""
from pathlib import Path
import json
import base64
import secrets
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="lunixi-java-source-") as directory:
    export = Path(directory) / "source"
    shutil.copytree(root, export, ignore=shutil.ignore_patterns("build", ".gradle", ".git", "__pycache__"))
    def scan():
        report = Path(directory) / "report.json"
        result = subprocess.run(["gitleaks", "dir", str(export), "--no-banner", "--redact",
            "--config", str(export / ".gitleaks.toml"), "--report-format", "json", "--report-path", str(report)],
            capture_output=True, text=True, timeout=60, check=False)
        return result.returncode, json.loads(report.read_text()) if report.exists() else []
    code, findings = scan()
    if code != 0 or findings:
        raise SystemExit("Clean source secret scan failed; inspect redacted Gitleaks diagnostics.")
    api_fixtures = [
        export / "src/test/java/com/lunixi/sdk/payment/PaymentsTest.java",
        export / "src/test/java/com/lunixi/sdk/physicalpos/PhysicalPosClientTest.java",
        export / "src/test/resources/physical-pos-parity.json",
    ]
    for fixture in api_fixtures:
        # JSON validity is immaterial: Gitleaks scans the complete raw text.
        with fixture.open("a") as handle:
            handle.write('\nString api_key = "' + secrets.token_hex(24) + '";\n')
    private_fixture = export / "src/test/java/com/lunixi/sdk/auth/Ed25519SignerTest.java"
    synthetic_der = bytes.fromhex("302e020100300506032b657004220420") + secrets.token_bytes(32)
    armour = "-----BEGIN " + "PRIVATE KEY-----\n" + base64.b64encode(synthetic_der).decode() + "\n-----END " + "PRIVATE KEY-----\n"
    with private_fixture.open("a") as handle:
        handle.write("\n" + armour)
    code, findings = scan()
    api_detected = all(any(f["RuleID"] == "generic-api-key" and Path(f["File"]) == fixture for f in findings)
                       for fixture in api_fixtures)
    private_detected = any(f["RuleID"] == "private-key" and f["File"].endswith("Ed25519SignerTest.java") for f in findings)
    if code != 1 or not api_detected or not private_detected:
        raise SystemExit("Secret scan API/private-key negative controls were not rejected.")
    # The exact reviewed UUID and source line must still be caught elsewhere.
    source = (root / "src/test/java/com/lunixi/sdk/physicalpos/PhysicalPosClientTest.java").read_text()
    reviewed_line = next(line for line in source.splitlines() if "static final String KEY =" in line)
    outside_fixture = export / "src/main/java/SecretScanNegativeControl.java"
    outside_fixture.write_text(reviewed_line + "\n")
    code, findings = scan()
    if code != 1 or not any(f["RuleID"] == "generic-api-key" and Path(f["File"]) == outside_fixture for f in findings):
        raise SystemExit("An exact reviewed fixture value outside its allowed path was ignored.")
print("Clean source secret scan passed; fresh API/private keys and the reviewed value outside its fixture path were rejected.")
