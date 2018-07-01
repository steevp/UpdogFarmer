#!/usr/bin/env python3
"""Add strings that haven't been translated yet."""

import os
import sys
import glob
import xml.etree.ElementTree as etree

# The script's path
SCRIPT_FOLDER = os.path.abspath(os.path.dirname(sys.argv[0]))
# Default language (English)
DEFAULT = SCRIPT_FOLDER + "/app/src/main/res/values/strings.xml"
# The languages we're comparing to
OTHER = glob.glob(SCRIPT_FOLDER + "/app/src/main/res/values-*/strings.xml")

def add_missing_strings():
    d = etree.parse(DEFAULT)
    for path in OTHER:
        o = etree.parse(path)
        missing = []
        for s in d.findall("string"):
            name = s.get("name")
            e = o.findall(".//*[@name='%s']" % name)
            if not e:
                missing.append(s)
        if missing:
            print("Updating " + path)
            oroot = o.getroot()
            oroot.append(etree.Comment(" Untranslated strings: "))
            for m in missing:
                oroot.append(m)
            oroot.append(etree.Comment(" End untranslated strings "))
            indent(oroot)
            o.write(path, encoding="UTF-8")
        else:
            print(path + " is up to date")

def indent(elem, level=0):
    i = "\n" + level*"    "
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = i + "    "
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
        for elem in elem:
            indent(elem, level+1)
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
    else:
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = i

if __name__ == "__main__":
    add_missing_strings()
