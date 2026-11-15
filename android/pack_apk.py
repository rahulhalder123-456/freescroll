"""Adds classes.dex and the Rust .so files to the aapt2-built base.apk."""
import sys, zipfile, pathlib

base, dex, libroot, out = sys.argv[1:5]
with zipfile.ZipFile(base) as zin, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write(dex, "classes.dex")
    for so in pathlib.Path(libroot).glob("*/*.so"):
        zout.write(so, f"lib/{so.parent.name}/{so.name}")
print("packed", out)
