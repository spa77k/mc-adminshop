#!/usr/bin/env python3
"""隔離Paperで怪しいお薬の設定・抽選・消費を検証する。"""
from pathlib import Path
import os
import shutil
import subprocess
import threading
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "server-data-26.1.2"
WORK = ROOT / "target/medicine-paper-smoke"
JAVA = os.environ.get("JAVA_BIN", "/opt/homebrew/opt/openjdk/bin/java")


def main():
    plugins = WORK / "plugins"
    plugins.mkdir(parents=True, exist_ok=True)
    for source, target in (
        (SOURCE / "paper-26.1.2-74.jar", WORK / "paper.jar"),
        (SOURCE / "plugins/Vault.jar", plugins / "Vault.jar"),
        (SOURCE / "plugins/EssentialsX-2.22.0.jar", plugins / "EssentialsX.jar"),
        (ROOT / "target/adminshop-1.0.0.jar", plugins / "AdminShop.jar"),
    ):
        shutil.copy2(source, target)
    config = plugins / "AdminShop/config.yml"
    config.parent.mkdir(exist_ok=True)
    shutil.copy2(ROOT / "src/main/resources/config.yml", config)
    with zipfile.ZipFile(plugins / "MedicineProbe.jar", "w") as jar:
        jar.writestr("plugin.yml", "name: MedicineProbe\nversion: 1\n"
                     "main: dev.spa.adminshop.MysteryMedicineProbe\n"
                     "api-version: '1.21'\ndepend: [AdminShop, Vault, Essentials]\n")
        for source in (ROOT / "target/test-classes/dev/spa/adminshop").glob("MysteryMedicineProbe*.class"):
            jar.write(source, "dev/spa/adminshop/" + source.name)
    (WORK / "eula.txt").write_text("eula=true\n")
    (WORK / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25584\nonline-mode=false\n"
        "spawn-protection=0\nmax-players=1\nlevel-type=minecraft:flat\n"
    )
    process = subprocess.Popen(
        [JAVA, "-Xms512M", "-Xmx1G", "-jar", "paper.jar", "--nogui"],
        cwd=WORK, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1,
    )
    lines = []
    done = threading.Event()

    def read_output():
        for line in process.stdout:
            lines.append(line)
            if "MEDICINE_PROBE_PASS" in line or "MEDICINE_PROBE_FAIL" in line:
                done.set()

    thread = threading.Thread(target=read_output, daemon=True)
    thread.start()
    try:
        if not done.wait(120):
            raise RuntimeError("Paperの検証が120秒以内に終わりませんでした")
    finally:
        if process.poll() is None:
            process.stdin.write("stop\n")
            process.stdin.flush()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
        thread.join(timeout=2)
    output = "".join(lines)
    if "MEDICINE_PROBE_PASS" not in output or "MEDICINE_PROBE_FAIL" in output:
        print(output[-8000:])
        raise SystemExit("怪しいお薬の検証に失敗しました")
    print("Paper 26.1.2: 100S・28種類・レベルI〜III・60〜300秒・両手の1個消費を確認しました。")


if __name__ == "__main__":
    main()
