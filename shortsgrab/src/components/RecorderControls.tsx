import { useRef, useState } from "react";

export default function RecorderControls() {
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const [recording, setRecording] = useState(false);

  async function start() {
    const stream = await navigator.mediaDevices.getDisplayMedia({
      video: true,
      audio: true,
    });

    const recorder = new MediaRecorder(stream);
    recorderRef.current = recorder;

    recorder.ondataavailable = (e) => chunksRef.current.push(e.data);
    recorder.onstop = () => {
      const blob = new Blob(chunksRef.current, { type: "video/webm" });
      const url = URL.createObjectURL(blob);

      const a = document.createElement("a");
      a.href = url;
      a.download = "shortsgrab.webm";
      a.click();

      chunksRef.current = [];
    };

    recorder.start();
    setRecording(true);
  }

  function stop() {
    recorderRef.current?.stop();
    setRecording(false);
  }

  return (
    <div style={{ marginTop: "16px" }}>
      {!recording ? (
        <button onClick={start}>Start Capture</button>
      ) : (
        <button onClick={stop}>Stop & Download</button>
      )}
    </div>
  );
}
