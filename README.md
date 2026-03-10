<p align="center">
  <img src="assets/orion_logo_512.png" width="240" alt="Orion Store Logo">
</p>

<h1 align="center">Orion Store</h1>

<p align="center">
  <a href="https://reactjs.org/"><img src="https://img.shields.io/badge/react-%2320232a.svg?style=flat&logo=react&logoColor=%2361DAFB" alt="React"></a>
  <a href="https://tailwindcss.com/"><img src="https://img.shields.io/badge/tailwindcss-%2338B2AC.svg?style=flat&logo=tailwind-css&logoColor=white" alt="TailwindCSS"></a>
  <a href="https://capacitorjs.com/"><img src="https://img.shields.io/badge/capacitor-%231199EE.svg?style=flat&logo=capacitor&logoColor=white" alt="Capacitor"></a>
</p>

<p align="center">
  <em>A transparent, multi-source app store powered by Open Source Git platforms</em><br>
  <em>Built for automation, trust, and community-driven distribution</em>
</p>

<p align="center">
  <a href="https://github.com/RookieEnough/Orion-Store/stargazers">
    <img src="https://img.shields.io/github/stars/RookieEnough/Orion-Store?style=social">
  </a>
  <a href="https://github.com/RookieEnough/Orion-Store/network/members">
    <img src="https://img.shields.io/github/forks/RookieEnough/Orion-Store?style=social">
  </a>
</p>

<p align="center">
  <a href="https://rookiezz.gumroad.com/l/hrpyb">
    <img src="https://img.shields.io/badge/Support%20me%20on-Gumroad-%23FF90E8?style=flat&logo=gumroad&logoColor=white" alt="Support me on Gumroad">
  </a>
</p>

<p align="center">
  <a href="https://github.com/RookieEnough/Orion-Store/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/RookieEnough/Orion-Store?style=flat-square">
  </a>
  <a href="https://github.com/RookieEnough/Orion-Store/commits/main">
    <img src="https://img.shields.io/github/last-commit/RookieEnough/Orion-Store?style=flat-square">
  </a>
  <a href="https://github.com/RookieEnough/Orion-Store/releases">
    <img src="https://img.shields.io/github/downloads/RookieEnough/Orion-Store/total?style=flat-square">
  </a>
</p>

---

## Overview 🌌

**Orion Store** is a modern, serverless app store that decentralizes app distribution. By leveraging public Git infrastructures, Orion ensures that every app you download is fetched directly from its source with no middleman.

### Multi-Source Support 🌐
Orion isn't just limited to one platform. It intelligently fetches and tracks apps across:
1. **GitHub** - The primary hub for open-source innovation.
2. **GitLab** - Robust support for diverse repository structures.
3. **Codeberg** - Embracing the independent and privacy-focused community.

---

## Key Highlights ⚡

- **Malware Scanner** – Integrated checks to ensure package integrity.
- **System Debloater** – Deep clean your device by removing unwanted system apps.
- **Package Extractor** – Easily extract and backup installed APKs.
- **Auto Updates** – Smart background detection for app updates.
- **Gamified Ad Support** – Support development through a rewarding, non-intrusive system.
- **Fully Serverless** – No centralized backend to fail or track you.
- **Extremely Lightweight** – High performance within a 5-6 MB footprint.

---

## Screenshots 📸

<div align="center">
  <table>
    <tr>
      <td><img src="assets/screenshot 1.jpg" width="200" alt="Screen 1" /></td>
      <td><img src="assets/screenshot 2.jpg" width="200" alt="Screen 2" /></td>
      <td><img src="assets/screenshot 3.jpg" width="200" alt="Screen 3" /></td>
      <td><img src="assets/screenshot 4.jpg" width="200" alt="Screen 4" /></td>
      <td><img src="assets/screenshot 5.jpg" width="200" alt="Screen 5" /></td>
    </tr>
    <tr>
      <td><img src="assets/screenshot 6.jpg" width="200" alt="Screen 6" /></td>
      <td><img src="assets/screenshot 7.jpg" width="200" alt="Screen 7" /></td>
      <td><img src="assets/screenshot 8.jpg" width="200" alt="Screen 8" /></td>
      <td><img src="assets/screenshot 9.jpg" width="200" alt="Screen 9" /></td>
      <td><img src="assets/screenshot 10.jpg" width="200" alt="Screen 10" /></td>
    </tr>
  </table>
</div>

---

## Architecture and Transparency 🔍

Orion is built around openness.

### App Warehouse

All apps live in the **[Orion Data](https://github.com/RookieEnough/Orion-data)** repository.

- `app.json` contains the full app catalog  
- Apps are added through community pull requests  
- No manual uploads or private binaries  

### Smart API Handling

- `mirror.json` intelligently bypasses GitHub API rate limits  
- Ensures stability even under heavy usage  

Every step is visible, reviewable, and reproducible.

---

## Themes 🎨

Orion supports multiple themes:

- Light  
- Dark  
- Dusk  
  A custom theme introduced with its own identity  

---

## Developer Mode 🛠️

Orion includes a hidden **Developer Mode** designed for power users.

### Unlock Method

- Tap the **Orion Store** header 8 times  

### Developer Features

- Advanced debugging options  
- App metadata inspection  
- Manual refresh and diagnostics  
- GitHub API configuration  

### Personal Access Token Support

Users can add their own GitHub **Personal Access Token** inside Developer Mode.

- Default API limit: 60 requests per hour  
- With PAT: up to 5000 requests per hour  

This improves performance without compromising transparency.

---

## Gamification and Badges 🏆

Orion includes **8 cosmetic badges**.

- Each badge has a unique hidden unlock condition  
- No public hints or documentation  
- Encourages exploration and curiosity  

Badges are purely cosmetic and do not affect app functionality.

---

## Supporting Development ❤️

Orion does not force monetization.

Users can support development in two optional ways:

### Buy Me a Coffee
<a href="https://www.buymeacoffee.com/rookiez" target="_blank">
  <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" width="120" alt="Buy Me A Coffee">
</a>

### Fuel The Code
A gamified system where users support the project by watching ads.

- Completely optional  
- No forced ads  
- Designed to be respectful and fun  

---

## Related Project

### [Morphe Auto Builds](https://github.com/RookieEnough/morphe-AutoBuilds)

- Built automatically using GitHub Actions  
- Uses the official Morphe CLI patcher  
- No manual uploads  
- Fully transparent and reproducible builds  

This project integrates cleanly with Orion Store.

---

## Contribution 🤝

Contributions are welcome.

- Submit new apps via Orion Data  
- Improve metadata or structure  
- Open pull requests for enhancements  

Help grow a clean, community driven app ecosystem.

---

## License 📄

Orion Store is licensed under the **MIT License**.

---

<p align="center">
  Made with 💜 by <strong>RookieZ</strong>
</p>
