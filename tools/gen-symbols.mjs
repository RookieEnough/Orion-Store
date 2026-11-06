// Regenerates app/src/main/java/com/orion/store/ui/Symbols.kt from Material Symbols Rounded (weight 400, optical 24).
// Usage: node tools/gen-symbols.mjs   (add new names to NAMES / FILLED below, then rerun)
import { writeFileSync } from 'node:fs';

const NAMES = `AccountBalanceWallet AccountTree Add AddCircle AddCircleOutline AddPhotoAlternate AdminPanelSettings Android Apps ArrowBack ArrowDownward ArrowForward
ArrowUpward AutoAwesome AutoFixHigh Autorenew Badge Block BlurOn Bolt BugReport Build Campaign CardGiftcard Chat Check CheckCircle ChevronRight CleaningServices
Close Cloud CloudOff Code Coffee Compress Computer Construction ContentCopy Contacts Contrast Dashboard DarkMode Delete DeleteForever DeleteOutline DeleteSweep
Description DeveloperBoard Diamond Dns DoneAll Download DownloadDone Downloading Draw Edit EditNote Egg EmojiEvents EnhancedEncryption ErrorOutline ExpandLess
ExpandMore Extension Favorite FavoriteBorder FileDownload FileUpload Flag Folder FolderOpen FontDownload FormatColorFill Fort Forum Gavel GppBad GppGood GppMaybe
Group Headphones HeartBroken HelpOutline History HistoryEdu Home HomeRepairService Image Info InstallMobile Inventory Inventory2 Key KeyboardArrowRight
KeyboardArrowUp Lan Layers LibraryAdd LightMode Lightbulb Link LinkOff LocalFireDepartment LocationOn Lock LockOpen Login MedicalServices Memory MenuBook Mic
MilitaryTech MonitorHeart MotionPhotosOff Movie MusicNote NewReleases Newspaper NoEncryption NotificationsOff Notifications OpenInNew Paid Palette Payments
PermMedia Person PhoneAndroid PhotoCamera PlayArrow PlayCircle Psychology Public QrCode2 QueryStats QuestionMark Radar Refresh RestartAlt Restore RocketLaunch
Save SaveAlt Schedule School Science Search SearchOff Security Settings Share Shield SkipNext SmartToy Sms Sort Speed SportsEsports Star Storage SwapHoriz Sync
SystemUpdate TaskAlt Terminal Timer ToggleOn TouchApp Tv Unarchive UnfoldMore Upload Verified VerifiedUser Videocam ViewInAr Visibility VisibilityOff
VolunteerActivism VpnLock Warning WarningAmber Wifi WifiOff Work WorkspacePremium
GridView MoreVert Tune FilterList SwapVert ArrowDropDown IosShare Category Sell Package2 CodeBlocks Mail Report TravelExplore TrendingUp Update
DownloadForOffline Translate TextFields DesktopWindows Smartphone Widgets Bookmark Explore Vibration ViewAgenda Animation FormatPaint Brightness6 AppsOutage
ChevronLeft CheckSmall Bedtime NorthEast OpenInFull Celebration Hub Policy Encrypted DataObject Rocket StarRate Swipe AdsClick Sailing
Tonality Routine WaterDrop Wallpaper Cast Backup DeveloperMode Gesture ViewQuilt ViewStream Flare Stars DragHandle Undo Redo
CallMerge RamenDining Crown Swords Skull Castle Tsunami AllInclusive Hive Orbit AddLink Publish FilterAlt`.split(/\s+/).filter(Boolean);

// Names whose Material Icons original was the solid half of an outline/solid pair map to FILL 1.
const SOLID = new Set(['Favorite', 'AddCircle', 'Delete']);
// Extra filled variants, exposed as <Name>Filled, for selected/emphasis states.
const FILLED = `Home Android Computer Tv GridView Star Verified CheckCircle Shield Info Settings Bolt EmojiEvents Lock Notifications Person Folder DarkMode LightMode
Diamond Widgets Bookmark Explore Category Code DesktopWindows Smartphone Download DownloadForOffline Apps Orbit`.split(/\s+/).filter(Boolean);
const MIRROR = new Set(['ArrowBack', 'ArrowForward', 'HelpOutline', 'KeyboardArrowRight', 'OpenInNew', 'ChevronRight', 'ChevronLeft', 'Sort', 'Login', 'NorthEast']);
const RENAME = { ErrorOutline: 'error', HelpOutline: 'help', DeleteOutline: 'delete', AddCircleOutline: 'add_circle', FavoriteBorder: 'favorite', WarningAmber: 'warning', SaveAlt: 'download'};

const snake = n => RENAME[n] ?? n.replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/([a-zA-Z])(\d)/g, '$1_$2').toLowerCase();
const url = (sym, fill) => `https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/${sym}/${fill ? 'fill1' : 'default'}/24px.svg`;
async function path(sym, fill) {
  const r = await fetch(url(sym, fill));
  if (!r.ok) throw new Error(`${sym} ${fill ? 'fill1' : 'default'}: ${r.status}`);
  return [...(await r.text()).matchAll(/ d="([^"]+)"/g)].map(m => m[1]).join(' ');
}
const jobs = [...NAMES.map(n => [n, n, SOLID.has(n)]), ...FILLED.map(n => [n + 'Filled', n, true])];
const out = [];
for (let i = 0; i < jobs.length; i += 24) {
  out.push(...await Promise.all(jobs.slice(i, i + 24).map(async ([k, n, fill]) => [k, await path(snake(n), fill), MIRROR.has(n)])));
}
// simple-icons minifies arc flags ("a1 1 0 00-4.8"); Compose's parser reads "00" as one number, so space them out.
function splitArcFlags(d) {
  const num = /^[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?/;
  const sep = c => c === ' ' || c === ',' || c === '\n' || c === '\t';
  let out = '', cmd = '', i = 0;
  const take = flag => { while (sep(d[i])) i++; if (flag) return d[i++]; const m = d.slice(i).match(num)[0]; i += m.length; return m; };
  while (i < d.length) {
    if (sep(d[i])) { i++; continue; }
    if (/[a-zA-Z]/.test(d[i])) { cmd = d[i++]; out += cmd; continue; }
    out += ' ' + (cmd === 'a' || cmd === 'A' ? [0, 0, 0, 1, 1, 0, 0].map(f => take(f)).join(' ') : take(false));
  }
  return out;
}
// Brand marks from simple-icons (CC0), 24x24 viewport, exposed as Sym.Brand<Name>.
const BRANDS = { GitHub: "github", GitLab: "gitlab", Codeberg: "codeberg", X: "x", Discord: "discord", KoFi: "kofi", PayPal: "paypal" };
for (const [k, slug] of Object.entries(BRANDS)) {
  const svg = await (await fetch(`https://cdn.jsdelivr.net/npm/simple-icons@latest/icons/${slug}.svg`)).text();
  out.push(["Brand" + k, splitArcFlags(svg.match(/ d="([^"]+)"/)[1]), false, true]);
}
const lines = out.sort((a, b) => a[0].localeCompare(b[0])).map(([k, d, m, brand]) => brand ? `    val ${k} get() = b("${k}", "${d}")` : `    val ${k} get() = s("${k}", "${d}"${m ? ', true' : ''})`);
writeFileSync('app/src/main/java/com/orion/store/ui/Symbols.kt', `// Generated by tools/gen-symbols.mjs from Material Symbols Rounded. Do not edit by hand.
package com.orion.store.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Material Symbols Rounded (FILL 0 unless suffixed Filled), parsed once on first use. */
object Sym {
    private val cache = HashMap<String, ImageVector>()
    private fun s(n: String, d: String, mirror: Boolean = false) = cache.getOrPut(n) {
        ImageVector.Builder(n, 24.dp, 24.dp, 960f, 960f, autoMirror = mirror).addGroup(translationY = 960f)
            .addPath(addPathNodes(d), fill = SolidColor(Color.Black)).clearGroup().build()
    }
    private fun b(n: String, d: String) = cache.getOrPut(n) { ImageVector.Builder(n, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(d), fill = SolidColor(Color.Black)).build() }

${lines.join('\n')}
}
`);
console.log(`wrote ${out.length} icons`);
