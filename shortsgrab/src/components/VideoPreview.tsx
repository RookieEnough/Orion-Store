import { getEmbedUrl } from "../utils/getEmbedUrl";
import type { Platform } from "../utils/detectPlatform";

type Props = {
  url: string;
  platform: Platform;
};

export default function VideoPreview({ url, platform }: Props) {
  const embedUrl = getEmbedUrl(url, platform);

  if (!embedUrl) return null;

  return (
    <iframe
      src={embedUrl}
      style={{
        width: "100%",
        height: "400px",
        marginTop: "16px",
        borderRadius: "8px",
      }}
      allow="autoplay; encrypted-media"
      allowFullScreen
    />
  );
}
