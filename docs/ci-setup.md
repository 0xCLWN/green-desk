# CI / Release setup

The release workflow (`.github/workflows/release.yml`) fires on any `v*` tag and publishes a signed APK to GitHub Releases.

## One-time setup

### 1. Generate a release keystore

```bash
keytool -genkeypair \
  -keystore green-release.jks \
  -alias green \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <store-password> \
  -keypass <key-password> \
  -dname "CN=Green VPN, O=Green, C=US" \
  -noprompt
```

Keep `green-release.jks` safe — losing it means you can never update the app on devices that have it installed.

### 2. Base64-encode the keystore

```bash
base64 -i green-release.jks | pbcopy   # macOS — copies to clipboard
```

### 3. Add secrets to the GitHub repo

Go to **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
|---|---|
| `KEYSTORE_B64` | Base64 output from step 2 |
| `KEY_ALIAS` | `green` (or whatever alias you used) |
| `STORE_PASSWORD` | The `--storepass` value from step 1 |
| `KEY_PASSWORD` | The `--keypass` value from step 1 |

### 4. Enable workflow write permissions

Go to **Settings → Actions → General → Workflow permissions** and set to **Read and write permissions**. This lets the workflow create GitHub Releases.

## Releasing

```bash
./release.sh          # bumps version, commits, tags
git push origin main && git push origin v<new-version>
```

The tag push triggers the workflow. The signed APK appears under **Releases** when it completes (~2 min).
