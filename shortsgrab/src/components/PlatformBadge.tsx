type Props = {
  platform: "instagram" | "youtube" | "facebook" | "unknown";
};

const colors: Record<Props["platform"], string> = {
  instagram: "#E1306C",
  youtube: "#FF0000",
  facebook: "#1877F2",
  unknown: "#666",
};

export default function PlatformBadge({ platform }: Props) {
  return (
    <span
      style={{
        background: colors[platform],
        padding: "6px 12px",
        borderRadius: "20px",
        fontSize: "12px",
        fontWeight: 600,
        textTransform: "uppercase",
        color: "#fff",
      }}
    >
      {platform}
    </span>
  );
}
