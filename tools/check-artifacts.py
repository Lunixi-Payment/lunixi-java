"""Verify the actual JDK17 artifact/POM/staging bundle, not build declarations alone."""
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
properties = dict(line.split("=", 1) for line in (root / "gradle.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
version, artifact = properties["lunixiVersion"], properties["lunixiArtifactId"]
assert properties["lunixiJavaRelease"] == "17"
archives = [root / f"build/libs/{artifact}-{version}{suffix}.jar" for suffix in ("", "-sources", "-javadoc")]
for path in archives:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        assert names and all(not n.startswith("/") and ".." not in Path(n).parts for n in names)
        assert not any(n.endswith((".pem", ".key", ".sqlite3")) or "/test/" in n for n in names)
        for name in names:
            if name.endswith("/"):
                continue
            value = archive.read(name)
            assert not re.search(rb"/(?:Users|home)/[^ /]+/|/tmp/lunixi-|git[.][a-z0-9.-]+", value), name
            if name.endswith(".class"):
                assert value[:4] == bytes.fromhex("cafebabe")
                assert struct.unpack(">H", value[6:8])[0] == 61, name
        if path == archives[0]:
            assert "com/lunixi/sdk/payment/CommerceService.class" in names
            assert "com/lunixi/sdk/fraud/FraudClient.class" in names
            assert all(n.startswith(("com/", "META-INF/")) for n in names)
pom = root / "build/publications/mavenJava/pom-default.xml"
document = ET.parse(pom).getroot()
namespace = {"p": "http://maven.apache.org/POM/4.0.0"}
assert document.findtext("p:groupId", namespaces=namespace) == "com.lunixi"
assert document.findtext("p:artifactId", namespaces=namespace) == artifact
assert document.findtext("p:version", namespaces=namespace) == version
assert not document.findall("p:dependencies/p:dependency", namespace), "Published runtime dependencies must remain zero."
assert document.findtext("p:scm/p:url", namespaces=namespace) == "https://github.com/Lunixi-Payment/lunixi-java"
with zipfile.ZipFile(root / f"build/central/{artifact}-{version}-bundle.zip") as archive:
    names = archive.namelist()
    prefix = f"com/lunixi/{artifact}/{version}/"
    assert all(n.startswith(prefix) or (n.endswith("/") and prefix.startswith(n)) for n in names)
    assert not any("maven-metadata" in n for n in names)
    assert prefix + f"{artifact}-{version}.pom" in names
    assert all(prefix + path.name in names for path in archives)
print("JDK17/class61, SDK-only JARs, public paths, zero-dependency POM and Maven bundle layout passed.")
