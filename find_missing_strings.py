#!/usr/bin/env python3
"""Outputs strings that haven't been translated yet."""

import os
import sys
import xml.etree.ElementTree as ET

# The script's path
SCRIPT_FOLDER = os.path.abspath(os.path.dirname(sys.argv[0]))
# Default language (English)
DEFAULT = SCRIPT_FOLDER + "/app/src/main/res/values/strings.xml"
# The language we're comparing to
OTHER = SCRIPT_FOLDER + "/app/src/main/res/values-%s/strings.xml"
CODES = ["de", "ru", "uk", "pl", "cs", "pt-rPT", "pt-rBR", "tr", "th", "zh", "zh-rCN"]

def main():
    d = ET.parse(DEFAULT).getroot()
    for l in CODES:
        path = OTHER % l
        o = ET.parse(path).getroot()
        missing = []
        for s in d.findall("string"):
            name = s.get("name")
            e = o.findall(".//*[@name='%s']" % name)
            if not e:
                missing.append(ET.tostring(s, "unicode").strip())
        if missing:
            print(path + ":")
            print("\n".join(missing))

if __name__ == "__main__":
    main()
