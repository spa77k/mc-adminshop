#!/usr/bin/env python3
"""隔離Paperで42種類の装飾ヘッドを読み込めることを確認する。"""

from pathlib import Path
import os
import shutil
import subprocess
import sys
import threading
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "server-data-26.1.2"
WORK = ROOT / "target/head-paper-smoke"
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
        if not source.is_file():
            raise SystemExit(f"必要なファイルがありません: {source}")
        shutil.copy2(source, target)

    with zipfile.ZipFile(plugins / "HeadProbe.jar", "w") as jar:
        jar.writestr("plugin.yml", "name: HeadProbe\nversion: 1\n"
                     "main: dev.spa.adminshop.PaperHeadProbe\n"
                     "api-version: '1.21'\ndepend: [AdminShop, Vault, Essentials]\n")
        for source in (ROOT / "target/test-classes/dev/spa/adminshop").glob("PaperHeadProbe*.class"):
            jar.write(source, "dev/spa/adminshop/" + source.name)

    (WORK / "eula.txt").write_text("eula=true\n")
    (WORK / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25582\nonline-mode=false\n"
        "spawn-protection=0\nmax-players=1\n"
    )

    process = subprocess.Popen(
        [JAVA, "-Xms512M", "-Xmx1G", "-jar", "paper.jar", "--nogui"],
        cwd=WORK, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1,
    )
    lines = []
    ready = threading.Event()
    probe_done = threading.Event()

    def read_output():
        for line in process.stdout:
            lines.append(line)
            if "Done (" in line:
                ready.set()
            if "HEAD_PROBE_PASS" in line or "HEAD_PROBE_FAIL" in line:
                probe_done.set()

    thread = threading.Thread(target=read_output, daemon=True)
    thread.start()
    try:
        if not ready.wait(90):
            raise RuntimeError("Paperが90秒以内に起動しませんでした")
        if not probe_done.wait(20):
            raise RuntimeError("テスト用Playerの検証が20秒以内に終わりませんでした")
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
    expected = "商品 6 件、装飾ヘッド 42 件を読み込みました。"
    if expected not in output or "HEAD_PROBE_PASS" not in output or "HEAD_PROBE_FAIL" in output:
        print(output[-6000:])
        raise SystemExit("AdminShopの装飾ヘッド検証に失敗しました")
    print("Paper 26.1.2で42種類の一覧と50S購入をテスト用Playerで確認しました。")
    print("GUI操作と購入の実クライアント確認は別途必要です。")


if __name__ == "__main__":
    main()
