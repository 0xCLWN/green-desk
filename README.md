# green

Android VPN client powered by xray-core.

## Releasing

1. Bump `app/build.gradle.kts`:
   ```kotlin
   versionCode = <previous + 1>
   versionName = "x.x.x"   // must match the tag below
   ```

2. Commit, tag, push:
   ```bash
   git add app/build.gradle.kts
   git commit -m "chore/ bump version to x.x.x"
   git tag vx.x.x
   git push origin main
   git push origin vx.x.x
   ```

The tag push triggers CI, which builds, signs with the keystore in repo secrets, and publishes the APK to GitHub Releases.

### Secrets required

| Secret | Description |
|---|---|
| `KEYSTORE_B64` | Base64-encoded `.jks` keystore (`base64 -i green-release.jks`) |
| `KEY_ALIAS` | Key alias inside the keystore |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |

### Version rules

- `versionCode` — integer, increment by 1 each release. Android uses this to detect upgrades.
- `versionName` — must equal the tag without the `v` prefix (`v1.0.3` → `1.0.3`). The in-app update checker compares this against the latest GitHub release tag.
- Patch releases (`x.x.z`) show only in Settings. Minor/major releases (`x.y.z`) also show a banner on the home screen.
