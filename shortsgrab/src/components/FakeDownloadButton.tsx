import type { Platform } from "../utils/detectPlatform";

type Props = {
  url: string;
  platform: Platform;
};

export default function FakeDownloadButton({ url, platform }: Props) {
  if (!url || platform === "unknown") return null;

  const handleClick = () => {
    alert(
      `⚠️ Download not supported in browser\n\n` +
      `Reason:\n` +
      `• ${platform.toUpperCase()} blocks direct downloads\n` +
      `• Requires backend service (yt-dlp)\n\n` +
      `Opening video in new tab instead.`
    );

    window.open(url, "_blank");
  };

  return (
    <button
      onClick={handleClick}
      style={{
        marginTop: "16px",
        padding: "10px 16px",
        borderRadius: "6px",
        background: "#2563eb",
        color: "#fff",
        border: "none",
        cursor: "pointer"
      }}
    >
      ⬇️ Download (Preview Mode)
    </button>
  );
}
