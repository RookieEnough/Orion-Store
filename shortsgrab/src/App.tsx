import { useState } from "react";
import UrlInput from "./components/UrlInput";
import VideoPreview from "./components/VideoPreview";
import RecorderControls from "./components/RecorderControls";
import FakeDownloadButton from "./components/FakeDownloadButton";
import PlatformBadge from "./components/PlatformBadge";
import { detectPlatform } from "./utils/detectPlatform";

export default function App() {
  const [url, setUrl] = useState("");
  const platform = detectPlatform(url);

  return (
    <div style={{ maxWidth: "800px", margin: "40px auto", color: "#fff" }}>
      <h1>🎬 ShortsGrab</h1>
      <p>Capture & download publicly accessible short videos.</p>

      <UrlInput url={url} setUrl={setUrl} />

      {platform !== "unknown" && (
        <div style={{ margin: "12px 0" }}>
          <PlatformBadge platform={platform} />
        </div>
      )}

      <VideoPreview url={url} platform={platform} />
      <FakeDownloadButton url={url} platform={platform} />
      <RecorderControls />
    </div>
  );
}
