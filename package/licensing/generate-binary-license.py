#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
"""Generate (or verify) LICENSE-binary and NOTICE-binary for PXF convenience packages.

The PXF server ships as a Spring Boot application JAR that bundles its entire
runtime classpath under BOOT-INF/lib.  The source tree's LICENSE and NOTICE
describe the source release only, so the binary packages need their own pair.

This script is the source of truth for keeping those two files honest:

  * ``bundled-components.tsv`` is the curated inventory - one row per JAR that
    ends up inside the application JAR, with its Maven coordinate, SPDX-ish
    license id and (for anything that is not plain Apache-2.0) the file under
    ``licenses-binary/`` carrying that component's license text.

  * ``--check`` asserts that the inventory still matches the JAR that was just
    built, and that the checked-in LICENSE-binary/NOTICE-binary match what this
    script would generate.  Any dependency added, removed or bumped fails the
    check until the inventory is updated.

Usage:

    generate-binary-license.py --generate [--jar PATH]
    generate-binary-license.py --check    [--jar PATH]
"""

import argparse
import fnmatch
import glob
import io
import os
import re
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, os.pardir, os.pardir))

INVENTORY = os.path.join(HERE, "bundled-components.tsv")
LICENSES_DIR = os.path.join(ROOT, "licenses-binary")
LICENSE_BINARY = os.path.join(ROOT, "LICENSE-binary")
NOTICE_BINARY = os.path.join(ROOT, "NOTICE-binary")
APACHE_LICENSE = os.path.join(ROOT, "LICENSE")

DEFAULT_JAR_GLOB = os.path.join(
    ROOT, "server", "build", "stage", "application", "pxf-app-*.jar"
)

# Licenses the ASF forbids in any release.  Listing such a component in
# LICENSE-binary would only document a violation, so fail loudly instead.
# See https://www.apache.org/legal/resolved.html#category-x
CATEGORY_X = {
    "JSON License",
    "GPL-2.0",
    "GPL-3.0",
    "LGPL-2.1",
    "LGPL-3.0",
    "AGPL-3.0",
    "CC-BY-NC",
}

NOTICE_MEMBER = re.compile(r"^(?:.*/)?NOTICE(?:[-._].*)?$", re.IGNORECASE)


def read_inventory():
    rows = []
    with io.open(INVENTORY, encoding="utf-8") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 4:
                sys.exit("malformed inventory row: %r" % line)
            jar, coord, license_id, license_file = parts[:4]
            note = parts[4] if len(parts) > 4 else ""
            rows.append(
                {
                    "jar": jar,
                    "coord": coord,
                    "license": license_id,
                    "file": license_file,
                    "note": note,
                }
            )
    return rows


def resolve_jar(explicit):
    if explicit:
        if not os.path.exists(explicit):
            sys.exit("application JAR not found: %s" % explicit)
        return explicit
    found = sorted(glob.glob(DEFAULT_JAR_GLOB))
    if not found:
        sys.exit(
            "application JAR not found at %s\n"
            "Build it first: make -C server stage-notest" % DEFAULT_JAR_GLOB
        )
    return found[-1]


def bundled_jars(jar_path):
    with zipfile.ZipFile(jar_path) as zf:
        return sorted(
            os.path.basename(n)
            for n in zf.namelist()
            if n.startswith("BOOT-INF/lib/") and n.endswith(".jar")
        )


def row_for(rows, jar_name):
    """Find the inventory row matching a bundled JAR.

    PXF's own modules carry the release version in their file name, so their
    rows use a glob (``pxf-api-*.jar``); third-party rows name the exact file so
    that a version bump forces the license to be re-reviewed.
    """
    for r in rows:
        if "*" in r["jar"] or "?" in r["jar"]:
            if fnmatch.fnmatch(jar_name, r["jar"]):
                return r
        elif r["jar"] == jar_name:
            return r
    return None


def collect_notices(jar_path, rows):
    """Pull the NOTICE file out of every bundled JAR that ships one."""
    notices = []
    with zipfile.ZipFile(jar_path) as outer:
        for name in sorted(outer.namelist()):
            if not (name.startswith("BOOT-INF/lib/") and name.endswith(".jar")):
                continue
            base = os.path.basename(name)
            row = row_for(rows, base)
            if row is None or base.startswith("pxf-"):
                continue
            try:
                inner = zipfile.ZipFile(io.BytesIO(outer.read(name)))
            except zipfile.BadZipFile:
                continue
            for member in inner.namelist():
                if member.endswith("/") or not NOTICE_MEMBER.match(member):
                    continue
                text = inner.read(member).decode("utf-8", "replace").strip()
                if text:
                    notices.append((row["coord"], text))
                break
    return notices


def check_category_x(rows):
    bad = [r for r in rows if r["license"] in CATEGORY_X]
    if bad:
        lines = [
            "ASF Category X license found in the binary distribution:",
            "",
        ]
        for r in bad:
            lines.append("    %s  (%s)  -> %s" % (r["coord"], r["license"], r["jar"]))
        lines += [
            "",
            "These components must not ship in an Apache release; documenting",
            "them in LICENSE-binary does not make the release compliant.",
            "See https://www.apache.org/legal/resolved.html#category-x",
        ]
        sys.exit("\n".join(lines))


def render_license(rows):
    with io.open(APACHE_LICENSE, encoding="utf-8") as fh:
        apache_text = fh.read()
    # The source LICENSE is the Apache 2.0 text followed by source-tree specific
    # sections; keep only the license text itself.
    cut = apache_text.find("\n=======================================================================")
    if cut != -1:
        apache_text = apache_text[:cut].rstrip() + "\n"

    out = [apache_text]
    out.append(
        "\n"
        "=======================================================================\n"
        "APACHE CLOUDBERRY PXF (INCUBATING) BINARY DISTRIBUTION\n"
        "=======================================================================\n"
        "\n"
        "Apache Cloudberry PXF (Incubating) is licensed under the Apache License,\n"
        "Version 2.0, reproduced above.\n"
        "\n"
        "The convenience binary packages (RPM, DEB and the binary tarball) ship the\n"
        "PXF server as a Spring Boot application JAR that bundles its entire runtime\n"
        "classpath.  The sections below list every component embedded in that JAR.\n"
        "This file describes the BINARY distribution; the source release is covered\n"
        "by the LICENSE file instead.\n"
    )

    groups = {}
    for r in rows:
        if r["jar"].startswith("pxf-"):
            continue
        groups.setdefault(r["license"], []).append(r)

    def heading(title):
        return "\n" + "-" * 71 + "\n" + title + "\n" + "-" * 71 + "\n\n"

    # Apache-2.0 first, then everything else alphabetically.
    order = ["Apache-2.0"] + sorted(k for k in groups if k != "Apache-2.0")
    for license_id in order:
        rows_for = groups.get(license_id)
        if not rows_for:
            continue
        if license_id == "Apache-2.0":
            out.append(heading("Apache License, Version 2.0"))
            out.append(
                "The following components are licensed under the Apache License,\n"
                "Version 2.0, the full text of which appears at the top of this file.\n\n"
            )
        else:
            out.append(heading(license_id))
        for r in sorted(rows_for, key=lambda x: x["coord"].lower()):
            line = "    " + r["coord"]
            if r["file"] and r["file"] != "-":
                line += "\n        license text: licenses-binary/" + r["file"]
            if r["note"]:
                line += "\n        note: " + r["note"]
            out.append(line + "\n")

    out.append(
        "\n"
        "=======================================================================\n"
        "\n"
        "Components carrying their own NOTICE file are reproduced in NOTICE-binary.\n"
    )
    return "".join(out)


def render_notice(notices):
    with io.open(os.path.join(ROOT, "NOTICE"), encoding="utf-8") as fh:
        base = fh.read().rstrip() + "\n"
    out = [base]
    out.append(
        "\n"
        "=======================================================================\n"
        "NOTICES FOR BUNDLED THIRD-PARTY COMPONENTS\n"
        "=======================================================================\n"
        "\n"
        "The convenience binary packages bundle the components listed in\n"
        "LICENSE-binary.  The notices below are reproduced verbatim from those\n"
        "components that ship a NOTICE file of their own.\n"
    )
    seen = set()
    for coord, text in notices:
        key = text.strip()
        if key in seen:
            continue
        seen.add(key)
        out.append(
            "\n"
            "-----------------------------------------------------------------------\n"
            "%s\n"
            "-----------------------------------------------------------------------\n"
            "\n%s\n" % (coord, text)
        )
    return "".join(out)


def verify_license_files(rows):
    missing = []
    for r in rows:
        if r["file"] and r["file"] != "-":
            if not os.path.exists(os.path.join(LICENSES_DIR, r["file"])):
                missing.append(r["file"])
    if missing:
        sys.exit(
            "license text missing from licenses-binary/: %s"
            % ", ".join(sorted(set(missing)))
        )
    referenced = {r["file"] for r in rows if r["file"] and r["file"] != "-"}
    # LICENSE-ongres-scram.txt is referenced from a note rather than its own row.
    referenced.add("LICENSE-ongres-scram.txt")
    on_disk = {f for f in os.listdir(LICENSES_DIR) if f.endswith(".txt")}
    orphan = on_disk - referenced
    if orphan:
        sys.exit(
            "licenses-binary/ contains files no component refers to: %s"
            % ", ".join(sorted(orphan))
        )


def compare_inventory(rows, jars):
    matched = set()
    added = []
    for j in jars:
        r = row_for(rows, j)
        if r is None:
            added.append(j)
        else:
            matched.add(r["jar"])
    removed = sorted(r["jar"] for r in rows if r["jar"] not in matched)
    if added or removed:
        msg = ["bundled-components.tsv is out of date:"]
        for j in added:
            msg.append("    + %s  (in the JAR, not in the inventory)" % j)
        for j in removed:
            msg.append("    - %s  (in the inventory, not in the JAR)" % j)
        msg.append("")
        msg.append(
            "Update package/licensing/bundled-components.tsv (and licenses-binary/)"
        )
        msg.append("then re-run with --generate.")
        sys.exit("\n".join(msg))


def check_makefile_license_tag(rows):
    """The RPM License: tag has to cover every license inside the package."""
    makefile = os.path.join(ROOT, "Makefile")
    tag = None
    with io.open(makefile, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"^LICENSE\s*\?=\s*(.+?)\s*$", line)
            if m:
                tag = m.group(1)
                break
    if tag is None:
        sys.exit("could not find the 'LICENSE ?=' assignment in Makefile")
    # "Public Domain" has no SPDX id; the Makefile spells it LicenseRef-Public-Domain.
    aliases = {"Public Domain": "LicenseRef-Public-Domain"}

    def covered(license_id):
        if license_id in tag:
            return True
        # A dual "A OR B" id is covered when either alternative appears in the
        # tag, since the packager may elect one of them.
        parts = [aliases.get(p, p) for p in license_id.split(" OR ")]
        return any(p in tag for p in parts)

    missing = sorted({r["license"] for r in rows if not covered(r["license"])})
    if missing:
        sys.exit(
            "Makefile LICENSE tag does not cover: %s\n"
            "Current value: %s" % (", ".join(missing), tag)
        )


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--generate", action="store_true", help="write the files")
    g.add_argument("--check", action="store_true", help="fail if the files are stale")
    ap.add_argument("--jar", help="path to the built pxf-app-*.jar")
    args = ap.parse_args()

    rows = read_inventory()
    jar = resolve_jar(args.jar)
    jars = bundled_jars(jar)

    compare_inventory(rows, jars)
    check_category_x(rows)
    verify_license_files(rows)
    check_makefile_license_tag(rows)

    license_text = render_license(rows)
    notice_text = render_notice(collect_notices(jar, rows))

    if args.generate:
        with io.open(LICENSE_BINARY, "w", encoding="utf-8", newline="") as fh:
            fh.write(license_text)
        with io.open(NOTICE_BINARY, "w", encoding="utf-8", newline="") as fh:
            fh.write(notice_text)
        print("wrote LICENSE-binary and NOTICE-binary from %s" % os.path.basename(jar))
        print("  %d bundled components (%d third-party)"
              % (len(jars), sum(1 for r in rows if not r["jar"].startswith("pxf-"))))
        return

    stale = []
    for path, want in ((LICENSE_BINARY, license_text), (NOTICE_BINARY, notice_text)):
        if not os.path.exists(path):
            stale.append(os.path.basename(path) + " is missing")
            continue
        with io.open(path, encoding="utf-8", newline="") as fh:
            if fh.read() != want:
                stale.append(os.path.basename(path) + " is out of date")
    if stale:
        sys.exit(
            "\n".join(stale)
            + "\n\nRegenerate with:\n"
            "    python3 package/licensing/generate-binary-license.py --generate"
        )
    print("LICENSE-binary and NOTICE-binary are up to date (%d bundled components)"
          % len(jars))


if __name__ == "__main__":
    main()
