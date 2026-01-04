type Props = {
  url: string;
  setUrl: (v: string) => void;
};

export default function UrlInput({ url, setUrl }: Props) {
  return (
    <input
      placeholder="Paste video URL…"
      value={url}
      onChange={(e) => setUrl(e.target.value)}
      style={{
        width: "100%",
        padding: "12px",
        fontSize: "16px",
        marginTop: "16px",
      }}
    />
  );
}
