# gxj「阅读·私有」Release 签名

本目录存放 **gxj** 风味私有包的 Release 签名密钥，用于与官方 `io.legado.app.ng.release` 并存安装（`io.legado.app.ng.gxj`）。

| 项 | 值 |
|---|---|
| Keystore 文件 | `keystore/gxj-private-release.jks` |
| Alias | `gxj-private` |
| Store 口令 | `legadoGxjPrivate` |
| Key 口令 | `legadoGxjPrivate` |

> **与 [v3.26.manual-models-private](https://github.com/swpu-cxm/legado_NG/releases/tag/v3.26.manual-models-private) 的证书不同**（旧 VM 密钥已丢失；当前 jks 为 2026-10-30 重新生成）。用本 keystore 签名的 APK **无法覆盖** 旧版「阅读·私有」，需先卸载再安装。

## 构建未签名 APK

在分支 `cursor/manual-ai-model-ids-16e1`（或含 gxj flavor 的分支）上：

```bash
./gradlew assembleGxjRelease -PUPDATE_CHANNEL=official
```

未签名产物示例：

`app/build/outputs/apk/gxj/release/legado_gxj_<version>.apk`

## 签名（zipalign + apksigner）

将 `BUILD_TOOLS` 换成本机 Android SDK 的 build-tools 目录（如 `~/Android/Sdk/build-tools/35.0.0`）。

```bash
KEYSTORE="keystore/gxj-private-release.jks"
ALIAS="gxj-private"
PASS="legadoGxjPrivate"
UNSIGNED="app/build/outputs/apk/gxj/release/legado_gxj_*.apk"   # 换成实际文件名
ALIGNED="/tmp/gxj-aligned.apk"
SIGNED="/tmp/legado_NG_gxj_private-signed.apk"
BT="$BUILD_TOOLS"

"$BT/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED"

"$BT/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$PASS" \
  --key-pass "pass:$PASS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$SIGNED" \
  "$ALIGNED"

"$BT/apksigner" verify --verbose --print-certs "$SIGNED"
```

## 发布 GitHub Release（示例）

```bash
gh release create v3.26.xxx \
  --repo swpu-cxm/legado_NG \
  --title "…" \
  --notes-file release-notes.md \
  "$SIGNED"
```

已发布示例：**[v3.26.share-json-import](https://github.com/swpu-cxm/legado_NG/releases/tag/v3.26.share-json-import)**（`legado_NG_gxj_share-json-import-signed.apk`）。
