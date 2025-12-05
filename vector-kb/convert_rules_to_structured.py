#!/usr/bin/env python3
"""
Convert natural-language "Rule" fields in non_dependency_fields.json into structured JSON objects.

Usage (mac):
  cd /Users/maheshramchandra/IdeaProjects/RoboSFTP/vector-kb
  python3 convert_rules_to_structured.py non_dependency_fields.json

This script will:
 - backup original file to <filename>.bak
 - replace Rule strings with structured dicts (enum, date, range, pattern, maxLength, const, raw)
 - write output back to the original file
"""
import json
import re
import sys
from pathlib import Path


def parse_rule_text(rule_text, example):
    if not rule_text or not isinstance(rule_text, str):
        return None

    s = rule_text.strip()

    # Static placeholder => const (use Example if available)
    if "Static Placeholder" in s or "Static placeholder" in s:
        value = example if example else re.sub(r"[^A-Za-z0-9_ -]", "", s)
        return {"type": "string", "const": value}

    # Date format
    if re.search(r"\byyyy[-/ ]?mm[-/ ]?dd\b", s, re.IGNORECASE) or "Date" in s and "yyyy" in s:
        return {"type": "date", "format": "yyyy-MM-dd"}

    # NRIC/FIN common pattern
    if re.search(r"NRIC|FIN", s, re.IGNORECASE):
        # common SG NRIC/FIN pattern: prefix S,F,T,G,M then 7 digits then letter
        return {"type": "string", "pattern": "^[SFTGM][0-9]{7}[A-Z]$"}

    # Domain\ADID -> AD account id format
    if "Domain\\ADID" in s or "AD ID" in s or "ADID" in s:
        return {
            "type": "string",
            "pattern": "^[^\\\\]+\\\\[^\\\\]+$",
            "description": "Domain\\ADID format (e.g. DOMAIN\\username)",
        }

    # Max length
    m = re.search(r"Max(?:imum)?\s*(\d{2,4})\s*characters", s, re.IGNORECASE)
    if m:
        return {"type": "string", "maxLength": int(m.group(1))}

    # Decimal / numeric ranges
    m_range = re.search(
        r"Allowed values:.*?([0-9]+(?:\.[0-9]+)?)\s*(?:to|-)\s*([0-9]+(?:\.[0-9]+)?)",
        s,
        re.IGNORECASE | re.S,
    )
    allow_vals = re.findall(r"\b999\b|\b9999\b|\b999\.?\b", s)
    if m_range:
        minv = float(m_range.group(1))
        maxv = float(m_range.group(2))
        rule = {"type": "number", "minimum": minv, "maximum": maxv}
        if allow_vals:
            rule["allow"] = [int(v) for v in set(allow_vals)]
        return rule

    # Integer ranges like "1 to 7, 999"
    m_int = re.search(r"Allowed values:.*?(\d+)\s*to\s*(\d+)", s, re.IGNORECASE | re.S)
    if m_int:
        minv = int(m_int.group(1))
        maxv = int(m_int.group(2))
        extras = re.findall(r"\b999\b", s)
        rule = {"type": "integer", "minimum": minv, "maximum": maxv}
        if extras:
            rule["allow"] = [999]
        return rule

    # Allowed enumerations: try to extract explicit values
    # Look for patterns like "Allowed values:" then list items separated by newline or comma,
    # possibly with comments ' // ' following values.
    if "Allowed values" in s or "Allowed Values" in s or re.search(r"Allowed value", s, re.IGNORECASE):
        # try to extract tokens before // or commas
        values = []
        for line in s.splitlines():
            # tokens before '//' or comment
            line = line.strip()
            if not line:
                continue
            # remove description comments after '//' or ':' etc
            token_part = re.split(r"//|:", line)[0]
            # split by commas
            parts = [p.strip() for p in token_part.split(",") if p.strip()]
            for p in parts:
                # if looks like a value (alnum, maybe symbols)
                if re.match(r"^[A-Za-z0-9\\-_\\/]+$", p):
                    values.append(p)
        values = list(dict.fromkeys(values))
        if values:
            return {"type": "enum", "values": values}

    # Single-word choices like "Complete\nIncomplete\nNA"
    lines = [l.strip() for l in s.splitlines() if l.strip()]
    if 1 < len(lines) <= 10 and all(len(l.split()) == 1 for l in lines):
        return {"type": "enum", "values": lines}

    # FOIS or small lists like "W //Walk" pattern -> capture the code before comment
    comment_matches = re.findall(r"([A-Za-z0-9]{1,6})\\s*//", s)
    if comment_matches:
        return {"type": "enum", "values": list(dict.fromkeys(comment_matches))}

    # Percent or 0-100 lists
    if re.search(r"0 to 100", s):
        return {"type": "number", "minimum": 0, "maximum": 100, "allow": [999]}

    # Fallback: keep raw text as a string field so it stays machine-readable
    return {"type": "raw", "text": s}


def convert_file(path):
    p = Path(path)
    if not p.exists():
        print("File not found:", path)
        return 1
    backup = p.with_suffix(p.suffix + ".bak")
    backup.write_bytes(p.read_bytes())
    arr = json.loads(p.read_text())
    changed = False
    for item in arr:
        rule = item.get("Rule", None)
        example = item.get("Example", None)
        if rule and (not isinstance(rule, dict)):
            new_rule = parse_rule_text(rule, example)
            if new_rule is not None:
                item["Rule"] = new_rule
                changed = True
            else:
                item["Rule"] = {"type": "raw", "text": rule}
                changed = True
    if changed:
        p.write_text(json.dumps(arr, indent=2, ensure_ascii=False))
        print(f"Updated file written; original backed up as {backup.name}")
    else:
        print("No changes detected.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: convert_rules_to_structured.py <json-file>")
        sys.exit(1)
    sys.exit(convert_file(sys.argv[1]))
