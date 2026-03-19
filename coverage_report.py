#!/usr/bin/env python3
"""Parse Kover XML coverage report and display per-file coverage, sorted worst-first.

Usage:
    python3 coverage_report.py [--threshold N] [--xml PATH] [--json PATH]

    --threshold N   Highlight files below N% line coverage (default: 80)
    --xml PATH      Path to coverage XML (default: .out/coverage.xml)
    --json PATH     Write JSON report to PATH (default: .out/coverage_report.json)
"""

import json
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


@dataclass
class FileCoverage:
    package: str
    filename: str
    lines_missed: int
    lines_covered: int
    branches_missed: int
    branches_covered: int

    @property
    def total_lines(self) -> int:
        return self.lines_missed + self.lines_covered

    @property
    def total_branches(self) -> int:
        return self.branches_missed + self.branches_covered

    @property
    def line_pct(self) -> float:
        if self.total_lines == 0:
            return 100.0
        return (self.lines_covered / self.total_lines) * 100

    @property
    def branch_pct(self) -> float:
        if self.total_branches == 0:
            return 100.0
        return (self.branches_covered / self.total_branches) * 100

    @property
    def qualified_name(self) -> str:
        pkg = self.package.replace("/", ".")
        return f"{pkg}/{self.filename}" if pkg else self.filename


RED = "\033[31m"
YELLOW = "\033[33m"
GREEN = "\033[32m"
BOLD = "\033[1m"
RESET = "\033[0m"


def color_pct(pct: float, threshold: float) -> str:
    if pct < threshold:
        return f"{RED}{pct:6.1f}%{RESET}"
    elif pct < 100.0:
        return f"{YELLOW}{pct:6.1f}%{RESET}"
    else:
        return f"{GREEN}{pct:6.1f}%{RESET}"


def parse_coverage(xml_path: Path) -> list[FileCoverage]:
    tree = ET.parse(xml_path)
    root = tree.getroot()
    results = []

    for package in root.findall("package"):
        pkg_name = package.get("name", "")
        for sourcefile in package.findall("sourcefile"):
            fname = sourcefile.get("name", "")
            lines_missed = 0
            lines_covered = 0
            branches_missed = 0
            branches_covered = 0

            for counter in sourcefile.findall("counter"):
                ctype = counter.get("type")
                missed = int(counter.get("missed", 0))
                covered = int(counter.get("covered", 0))
                if ctype == "LINE":
                    lines_missed = missed
                    lines_covered = covered
                elif ctype == "BRANCH":
                    branches_missed = missed
                    branches_covered = covered

            results.append(FileCoverage(
                package=pkg_name,
                filename=fname,
                lines_missed=lines_missed,
                lines_covered=lines_covered,
                branches_missed=branches_missed,
                branches_covered=branches_covered,
            ))

    return results


def write_json_report(files: list[FileCoverage], threshold: float, json_path: Path) -> None:
    total_lines = sum(f.total_lines for f in files)
    total_covered = sum(f.lines_covered for f in files)
    overall_pct = round((total_covered / total_lines * 100) if total_lines > 0 else 100.0, 2)

    report = {
        "threshold": threshold,
        "summary": {
            "overall_line_pct": overall_pct,
            "lines_covered": total_covered,
            "lines_total": total_lines,
            "files_total": len(files),
            "files_below_threshold": len([f for f in files if f.line_pct < threshold]),
        },
        "files": [
            {
                "file": f.qualified_name,
                "line_pct": round(f.line_pct, 2),
                "branch_pct": round(f.branch_pct, 2),
                "lines_covered": f.lines_covered,
                "lines_missed": f.lines_missed,
                "lines_total": f.total_lines,
                "branches_covered": f.branches_covered,
                "branches_missed": f.branches_missed,
                "branches_total": f.total_branches,
            }
            for f in files
        ],
    }

    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(report, indent=2) + "\n")
    print(f"JSON report written to: {json_path}")


def main():
    threshold = 80.0
    xml_path = Path(".out/coverage.xml")
    json_path = Path(".out/coverage_report.json")

    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == "--threshold" and i + 1 < len(args):
            threshold = float(args[i + 1])
            i += 2
        elif args[i] == "--xml" and i + 1 < len(args):
            xml_path = Path(args[i + 1])
            i += 2
        elif args[i] == "--json" and i + 1 < len(args):
            json_path = Path(args[i + 1])
            i += 2
        else:
            print(f"Unknown arg: {args[i]}", file=sys.stderr)
            sys.exit(1)

    if not xml_path.exists():
        print(f"Coverage XML not found at [{xml_path}]. Run coverage.sh first.", file=sys.stderr)
        sys.exit(1)

    files = parse_coverage(xml_path)
    # Sort by line coverage ascending (worst first), then by name
    files.sort(key=lambda f: (f.line_pct, f.qualified_name))

    # Write JSON report
    write_json_report(files, threshold, json_path)

    below_threshold = [f for f in files if f.line_pct < threshold]
    above_threshold = [f for f in files if f.line_pct >= threshold]

    # Header
    print(f"\n{BOLD}Code Coverage Report{RESET} (threshold: {threshold:.0f}%)")
    print(f"Source: {xml_path}\n")

    # Summary
    total_lines = sum(f.total_lines for f in files)
    total_covered = sum(f.lines_covered for f in files)
    overall_pct = (total_covered / total_lines * 100) if total_lines > 0 else 100.0
    print(f"Overall line coverage: {color_pct(overall_pct, threshold)}  ({total_covered}/{total_lines} lines)")
    print(f"Files below {threshold:.0f}%: {RED}{len(below_threshold)}{RESET} / {len(files)}")
    print()

    if below_threshold:
        print(f"{BOLD}{RED}── Files BELOW {threshold:.0f}% threshold ──{RESET}")
        print(f"{'File':<70} {'Lines':>8} {'Line%':>8} {'Branch%':>8} {'Missed':>8}")
        print("─" * 102)
        for f in below_threshold:
            print(
                f"{f.qualified_name:<70} "
                f"{f.lines_covered}/{f.total_lines:>4}  "
                f"{color_pct(f.line_pct, threshold)}  "
                f"{color_pct(f.branch_pct, threshold)}  "
                f"{RED}{f.lines_missed:>6}{RESET}"
            )
        print()

    if above_threshold:
        print(f"{BOLD}{GREEN}── Files at or above {threshold:.0f}% ──{RESET}")
        print(f"{'File':<70} {'Lines':>8} {'Line%':>8} {'Branch%':>8}")
        print("─" * 94)
        for f in above_threshold:
            print(
                f"{f.qualified_name:<70} "
                f"{f.lines_covered}/{f.total_lines:>4}  "
                f"{color_pct(f.line_pct, threshold)}  "
                f"{color_pct(f.branch_pct, threshold)}"
            )
        print()


if __name__ == "__main__":
    main()
