<div align="center">
  <img src="https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/assets/orion_logo_512.png" width="120" height="120" alt="OrionStore Logo" />
  
  # OrionStore
  
  **The Modern, Serverless App Store for the Open Web.**
  
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![React](https://img.shields.io/badge/react-%2320232a.svg?style=flat&logo=react&logoColor=%2361DAFB)](https://reactjs.org/)
  [![TailwindCSS](https://img.shields.io/badge/tailwindcss-%2338B2AC.svg?style=flat&logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)
  [![TypeScript](https://img.shields.io/badge/typescript-%23007ACC.svg?style=flat&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
  [![GitHub Actions](https://img.shields.io/badge/github%20actions-%232671E5.svg?style=flat&logo=githubactions&logoColor=white)](https://github.com/features/actions)

  <p align="center">
    <a href="#-features">Features</a> •
    <a href="#-how-it-works">Architecture</a> •
    <a href="#-deployment">Deployment</a> •
    <a href="#-auto-mirror-system">Auto-Mirror</a>
  </p>
</div>

---

## 📱 Preview

<div align="center">
  <!-- REPLACE THE LINK BELOW WITH YOUR ACTUAL DEMO VIDEO/GIF LINK -->
  <a href="https://your-demo-video-link.com">
    <img src="https://via.placeholder.com/800x400.png?text=Click+to+Watch+App+Demo" alt="OrionStore Demo Video" style="border-radius: 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.2);" width="100%" />
  </a>
</div>

<br />

<div align="center">
  <img src="https://via.placeholder.com/200x400.png?text=Home+Screen" height="350" alt="Home Screen" style="border-radius: 15px; margin: 5px;" />
  <img src="https://via.placeholder.com/200x400.png?text=Dark+Mode" height="350" alt="Dark Mode" style="border-radius: 15px; margin: 5px;" />
  <img src="https://via.placeholder.com/200x400.png?text=App+Details" height="350" alt="App Details" style="border-radius: 15px; margin: 5px;" />
</div>

---

## 🚀 Features

OrionStore is a **Progressive Web App (PWA)** that acts as a fully functional App Store without requiring a dedicated backend server. It runs entirely on GitHub.

*   **🎨 Material You 3 Design:** A vibrant, Gen Z aesthetic featuring "Acid" and "Neon" accents with smooth animations.
*   **🌗 Adaptive Theming:** Seamless Light, Dusk, and Dark modes.
*   **☁️ Serverless Architecture:** Powered 100% by GitHub JSON & Releases. No database required.
*   **🤖 Auto-Mirroring Engine:** Built-in scraper (Puppeteer) that finds updates for apps like Spotify, Instagram, etc., and re-uploads them to your repo automatically.
*   **⚡ Smart Caching:** LocalStorage caching strategy for instant loads and offline capability.
*   **📱 Responsive:** Native-like experience on Android, fully functional on Desktop/PC.

---

## 🛠 How It Works

OrionStore uses a unique **"Repo-as-a-Backend"** approach:

1.  **Frontend (`App.tsx`):** Fetches `config.json` and `apps.json` from the repository.
2.  **Database (`apps.json`):** Contains metadata (Name, Icon, Description) and links to GitHub Repositories.
3.  **The Engine:**
    *   The app checks the `mirror.json` file for the absolute latest releases.
    *   If `releaseKeyword` is set, it scans the **last 10 releases** of a repo to find the specific app variant you need (supporting multi-app monorepos).
    *   It compares the remote version with the local version stored in the browser/app.

---

## 🤖 Auto-Mirror System

This is the heart of the automation. You don't need to manually upload APKs.

### 1. Configuration
Edit `mirror_config.json` to tell the bot which apps to track.

```json
[
  {
    "id": "instagram-mod",
    "name": "Instagram",
    "downloadUrl": "https://an1.com/1029-instagram-apk.html",
    "mode": "scrape",
    "wait": 30000
  }
]
```

### 2. The Workflow (`.github/workflows/auto_mirror.yml`)
*   Runs daily at **00:00 UTC**.
*   **Direct Mode:** Wget/Curl direct links.
*   **Scrape Mode:** Uses **Puppeteer** with stealth plugins to bypass Cloudflare, navigate download pages (like AN1 or APKDone), and extract the APK.
*   **Publishing:** It parses the APK via `aapt` to get the *real* internal version number, tags it, and uploads it to GitHub Releases.

### 3. The Generator (`mirror_generator.py`)
*   Runs after every release.
*   Scans your Releases page.
*   Updates `mirror.json` with direct download links so the Frontend doesn't hit GitHub API rate limits.

---

## 📦 Deployment (Self-Hosting)

You can host your own version of OrionStore in less than 5 minutes.

1.  **Fork** this repository.
2.  Navigate to **Settings > Pages**.
3.  Select `Source: Deploy from a branch` -> `main` -> `/ (root)`.
4.  Click **Save**.
5.  Edit `constants.ts` and `apps.json` to point to your new repository URLs.

### Local Development

```bash
# 1. Clone the repo
git clone https://github.com/yourname/OrionStore.git

# 2. Install dependencies
npm install

# 3. Start development server
npm start
```

---

## 🤝 Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

---

## 💖 Support

This project is open-source and free. If you enjoy using it, consider buying me a coffee!

<a href="https://ko-fi.com/rookie_z">
  <img src="https://storage.ko-fi.com/cdn/kofi2.png?v=3" alt="Buy Me A Coffee" height="40" />
</a>

---

<div align="center">
  <p>Made with 💜 for Geeks by <a href="https://github.com/RookieEnough">RookieZ</a></p>
</div>

