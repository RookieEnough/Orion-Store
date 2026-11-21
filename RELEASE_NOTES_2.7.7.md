# Orion Store 2.7.7

Orbit follows apps more like Obtainium, downloads and installs report on the screen you are on, My Apps only lists what is actually on the phone, and package inspection respects your network preferences.

## 🪐 Orbit

* **MicroG RE Is Allowed:** Orbit no longer refuses MicroG RE just because its package name contains "revanced". ReVanced apps themselves are still refused.
* **Private Release Files:** A saved GitHub, GitLab or Codeberg token now downloads release files from private repositories, not only lists them.
* **Track Only:** Follow new versions without Install or Update, from Fine-tune or the long-press menu. Obtainium's track-only flag comes in and goes out with an export.
* **Wait Before Offering:** Hold a new Orbit release for 1, 2, 3 or 7 days, globally or on one app. The newest release that is old enough is the one offered. Obtainium's minimum update age is imported and exported.
* **Nightly Builds:** Nightly, daily and snapshot builds stay in the version picker even when pre-releases are off. After you install one, later updates stay on that channel.
* **Latest Tag:** A GitHub release newer than the one GitHub marks Latest is labeled Nightly, so a Nightly or Beta published ahead of stable no longer wears a Stable badge.
* **Already Installed:** An Orbit app whose package is already on the phone shows as installed, including when the same app is also in the Orion library, or when the library copy is a different variant such as a no-icon build. Download is not offered again for that copy.
* **Also in the Library:** Adding an app the store already has keeps both. A bookmark on the Orbit card opens the library page, and a shared app keeps a library mark.
* **Discover Search:** Searching the Obtainium directory keeps the matches it finds. Results no longer flash and then disappear.
* **Check on Your Schedule:** Pick how often Orion looks for updates, from every 15 minutes to once a week, or only while Orion is open. Orbit sources are checked on that schedule even when auto-update downloads are off.
* **AppGallery Downloads:** An AppGallery link follows Huawei's download redirect to the APK. The app page shows that build and its size, and Download fetches the file.

## ⬇️ Downloads and installs

* **Wi-Fi Only, On This Screen:** Starting a download on mobile data asks on the page you are on, and can allow that one download. Other downloads still wait for Wi-Fi.
* **Auto-Update on Wi-Fi Only:** A separate switch makes background updates wait for Wi-Fi, even when downloads you start are allowed on mobile data.
* **What Auto-Update Covers:** Auto-update covers installed apps Orion manages, including Orbit apps that are not paused or set to track only. When an Orbit app is also in the library, the library copy is the one updated. Skip auto-update is on the app's page and in My Apps. A manual Update still works.
* **Clear Queue:** The downloads sheet can clear active downloads and ready installs together. A stuck Install can be stopped from the same row.
* **Install When the Stream Closes:** If a silent install drops the connection on a large APK, Orion retries, then hands the saved file to Android's installer. The APK stays on the phone, and the explanation offers the system installer.
* **Remove APK:** The control beside Install is a bin, labeled Remove APK.
* **Shizuku-Next:** Shizuku-Next shows in the installer list. Choosing it installs through the Shizuku binder when that service is running.
* **Version on the Update:** An update shows the installed version and the new one, on the app page and in the queue. Stay on this version holds updates past the build you have.
* **No Install Toast on the App Page:** After a download finishes on the app you are looking at, the Install button is the prompt. The snackbar no longer covers it.

## 🔍 Inspect

* **Asks on Mobile Data:** Inspect downloads the build only after the same Wi-Fi question Download asks. One tap still allows that single download.
* **Report Stays with the Build:** Tracker info stays while this version and source are the ones offered. A newer release, or a changed source link, drops it. Removing the saved APK does not block Download.

## 📱 My Apps, the store and polish

* **Installed Means Installed:** Uninstalled apps leave My Apps. The Installed list is only apps on the phone now.
* **Pills Follow the Swipe:** Swiping between My Apps lists scrolls the filter pills with the page.
* **Screenshots Stop at the Ends:** Screenshot strips and the featured banner scroll through their real set and stop, instead of looping.
* **Wide Screenshots in the Banner:** A landscape screenshot sits in the featured banner the same way a tall one does.
* **Counts Sit Inside the Icons:** The download count and the settings count are drawn inside the header, so the circle is not cut off.
* **Download Count:** When a source reports how many times its release files were downloaded, that count sits with the stars under the developer name.
* **System Theme:** System follows the phone's light or dark setting, and the theme button cycles it with Light, Dusk, Dark and OLED.
* **What's New, in Plain Text:** Release notes drop Markdown markers, so headings and bullets read as text.
* **Submissions Open:** Generating a GitHub issue encodes characters that were making GitHub return an error page.
