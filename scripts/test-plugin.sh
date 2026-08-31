#!/usr/bin/env bash
# ローカルのPaperサーバーでAdminShopが有効になるところまでを確認する。
# GUIの表示・購入・死亡時の保護は、起動後にクライアントから localhost:25567 へ接続して確かめる。
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

SERVER_DIR="server-data-26.1.2"
PAPER_JAR="paper-26.1.2-74.jar"
JAVA_BIN="${JAVA_BIN:-/opt/homebrew/opt/openjdk/bin/java}"

if [[ ! -x "$JAVA_BIN" ]]; then
  echo "java が見つかりません: $JAVA_BIN" >&2
  echo "JAVA_BIN 環境変数で場所を指定してください。" >&2
  exit 1
fi

if [[ ! -f "$SERVER_DIR/$PAPER_JAR" ]]; then
  echo "$SERVER_DIR/$PAPER_JAR がありません。本番と同じPaperのJARを置いてください。" >&2
  exit 1
fi

echo "[1/3] プラグインをビルドしています"
mvn -B -q package

echo "[2/3] JAR をテストサーバーへ配置しています"
cp target/adminshop-1.0.0.jar "$SERVER_DIR/plugins/AdminShop.jar"

echo "[3/3] テストサーバーを起動して確認しています"
cd "$SERVER_DIR"
output="$(
  ( sleep 70; echo "plugins"; sleep 2; echo "ashop reload"; sleep 3; echo "stop" ) \
    | "$JAVA_BIN" -Xms1G -Xmx1G -jar "$PAPER_JAR" --nogui 2>&1
)"

echo "$output" | grep -E "AdminShop|ショップ|Done \(" || true

if ! grep -q "Enabling AdminShop" <<<"$output"; then
  echo "AdminShop が有効になりませんでした。" >&2
  exit 1
fi
if grep -q "Disabling AdminShop v1.0.0" <<<"$output" && grep -q "Error occurred while enabling AdminShop" <<<"$output"; then
  echo "AdminShop の有効化中にエラーが出ました。" >&2
  exit 1
fi
if ! grep -q "設定を読み込み直しました" <<<"$output"; then
  echo "/ashop reload が動きませんでした。" >&2
  exit 1
fi

echo "起動確認は成功しました。GUI と死亡時の保護は実際に接続して確かめてください。"
