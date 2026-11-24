# CallGuard AI website deployment

## Vercel website

The static website lives in `website/`. The root `vercel.json` sets the output directory.

1. Sign in to Vercel and choose **Add New → Project**.
2. Import `MAINAKRUDRA/CallGuard-AI` from GitHub.
3. Keep project root directory as `./` (repository root).
4. Framework preset: **Other**.
5. Leave build command and install command empty.
6. Set output directory to `website` (also declared in `vercel.json`).
7. Deploy. Future pushes to `main` trigger deployments.

Vercel assigns the actual project domain; confirm it in the Vercel dashboard.

## APK publishing

The website download button points to:

`https://github.com/MAINAKRUDRA/CallGuard-AI/releases/latest/download/callguard-debug.apk`

The workflow `.github/workflows/publish-apk-release.yml` builds and attaches this APK to a GitHub prerelease when a `v*` tag is pushed. It can also be started manually from GitHub Actions.

To publish a versioned test build:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Wait for **Publish Android APK Release** to complete, then verify the asset on GitHub Releases. The website download link then resolves to the latest published APK.

## Release limitation

This workflow publishes a debug-signed test APK, not a Play Store production release. A production release should use a protected signing key stored in GitHub Actions secrets. Never commit signing credentials.
