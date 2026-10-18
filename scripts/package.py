#!/usr/bin/env python3
"""Package the already-built APK and complete source without SDKs, caches, or signing keys."""
from pathlib import Path
import hashlib
import shutil
import zipfile

root = Path(__file__).resolve().parent.parent
apk = root / "app/build/outputs/apk/debug/app-debug.apk"
if not apk.is_file():
    raise SystemExit("Build first: ./gradlew :app:assembleDebug :app:lintDebug")
out = root / "dist"
out.mkdir(exist_ok=True)
shutil.copy2(apk, out / "AURA-debug.apk")
shutil.copy2(apk, out / "AURA-1.2.apk")
shutil.copy2(root / "docs/ANDROID_INSTALL.md", out / "INSTALL_ANDROID.md")
shutil.copy2(root / "README.md", out / "BUILD_AND_RUN.md")
shutil.copy2(root / "docs/VALIDATION.md", out / "VALIDATION.md")

entries = [
    ".gitignore", "README.md", "LICENSE", "THIRD_PARTY_NOTICES.md",
    "settings.gradle", "build.gradle", "gradle.properties", "gradlew", "gradlew.bat",
    "package.json", "package-lock.json", "playwright.config.js", "app/build.gradle",
    "app/src", "gradle", "docs", "tests", "scripts", "supabase",
]
with zipfile.ZipFile(out / "AURA-source.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    for entry in entries:
        path = root / entry
        files = sorted(path.rglob("*")) if path.is_dir() else [path]
        for source in files:
            if source.is_file() and "__pycache__" not in source.parts:
                archive.write(source, "AURA/" + source.relative_to(root).as_posix())

shutil.copy2(root / "docs/SUPABASE_SETUP.md", out / "SUPABASE_SETUP.md")
shutil.copy2(root / "docs/SECURITY_STATUS.md", out / "SECURITY_STATUS.md")
shutil.copy2(root / "supabase/migrations/202610180001_aura_backups.sql", out / "AURA-database.sql")
names = ["AURA-debug.apk", "AURA-1.2.apk", "AURA-source.zip", "INSTALL_ANDROID.md", "BUILD_AND_RUN.md", "VALIDATION.md", "SUPABASE_SETUP.md", "SECURITY_STATUS.md", "AURA-database.sql"]
lines = []
for name in names:
    data = (out / name).read_bytes()
    lines.append(hashlib.sha256(data).hexdigest() + "  " + name)
    print(f"{name}: {len(data):,} bytes")
(out / "SHA256SUMS").write_text("\n".join(lines) + "\n")
