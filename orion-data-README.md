<p align="center">
  <img src="https://raw.githubusercontent.com/RookieEnough/Orion-Store/main/assets/orion_logo_512.png" width="280" alt="Orion Store logo">
</p>

<h1 align="center">Orion Data</h1>

<p align="center">
  <a href="https://github.com/RookieEnough/Orion-Data/stargazers"><img src="https://img.shields.io/github/stars/RookieEnough/Orion-Data?logo=github&color=yellow" alt="Stars"></a>
  <a href="https://github.com/RookieEnough/Orion-Data/network/members"><img src="https://img.shields.io/github/forks/RookieEnough/Orion-Data?logo=github&color=orange" alt="Forks"></a>
</p>

<p align="center">
  Public catalog, configuration, and metadata for <a href="https://github.com/RookieEnough/Orion-Store">Orion Store</a>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/JSON-000000?style=for-the-badge&logo=json&logoColor=white" alt="JSON">
  <img src="https://img.shields.io/badge/Open_Data-2EA44F?style=for-the-badge&logo=opendata&logoColor=white" alt="Open Data">
  <img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub">
</p>

<br>

## Overview

This repository contains the public data layer for Orion Store. Everything here is plain JSON that anyone can read, fork, or contribute to. The client app pulls from these files to populate the store catalog, show release metadata, and handle configuration.

> The goal is simple: store data should be as transparent as the app that uses it.

## Repository structure

| File | Purpose |
|------|---------|
| `apps.json` | Main catalog of apps available in Orion Store |
| `config.json` | Remote configuration and feature flags |
| `notices.json` | Service notices and announcements |
| `metadata/` | Per-app metadata, screenshots, and descriptions |
| `mirrors/` | Mirror source definitions and fallback endpoints |

## The catalog format

Each app entry in `apps.json` follows this structure:

```json
{
  "id": "com.example.app",
  "name": "App Name",
  "author": "Developer Name",
  "description": "Short description of the app",
  "source": {
    "type": "github",
    "repo": "owner/repo-name"
  },
  "categories": ["productivity", "utilities"],
  "license": "MIT",
  "screenshots": ["url1", "url2"],
  "icon": "https://example.com/icon.png"
}
```

## Contributing apps

Want to add an app to Orion Store? Open a PR with:

1. App entry added to `apps.json`
2. Metadata file in `metadata/{app-id}.json`
3. Screenshots hosted on a reliable CDN (or include in PR)

**Requirements:**
- App must have public releases on GitHub, GitLab, or Codeberg
- Source code should be publicly available
- No malware, adware, or deceptive software

## Automation

This repo uses GitHub Actions to:
- Validate JSON schema on PRs
- Sync release metadata from upstream sources
- Generate delta updates for the client

## Why separate repos?

Keeping data and client code in separate repositories means:
- Data changes don't require app updates
- Community can contribute apps without touching client code
- Clear separation between UI bugs and catalog issues
- Multiple clients could theoretically use the same data

## Links

- **Client app**: [Orion-Store](https://github.com/RookieEnough/Orion-Store)
- **Discord**: [discord.gg/CrM6y4ujnq](https://discord.com/invite/CrM6y4ujnq)
- **Support**: [Buy Me a Coffee](https://www.buymeacoffee.com/rookiez)

---

<p align="center">
  Data should be open. Catalogs should be inspectable.
</p>
