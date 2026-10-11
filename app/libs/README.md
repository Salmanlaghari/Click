# libbox.aar — sing-box core for Android VPN

This directory holds `libbox.aar`, the sing-box VPN core compiled as an
Android library via gomobile.

## Where it comes from

**NEVER commit a third-party prebuilt binary.** The AAR is built from
OFFICIAL SagerNet/sing-box source:

1. **CI (preferred):** Run the `build-libbox.yml` workflow (or it runs
   automatically when `libbox.version` changes). It:
   - Installs Go + Android NDK + gomobile
   - Clones `https://github.com/SagerNet/sing-box` at the pinned version
   - Runs `gomobile bind` with the tags in the workflow
   - Uploads `libbox.aar` as an artifact + caches it
2. **Manual build:** Follow the same `gomobile bind` command in
   `.github/workflows/build-libbox.yml` on a machine with Go 1.26+, JDK 17,
   and Android NDK r26.

## Wiring

`app/build.gradle.kts` includes:

```kotlin
implementation(files("libs/libbox.aar"))
```

The build FAILS with a clear error if `libbox.aar` is missing — it is never
silently skipped, because a "VPN" without the tunnel core would be dishonest.

## Version pin

See `libbox.version` — single source of truth for the sing-box version.
Bump it + run `build-libbox.yml` to upgrade.
