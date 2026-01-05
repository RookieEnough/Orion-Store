export function Toast({ message }: { message: string }) {
  return (
    <div className="fixed top-36 left-1/2 -translate-x-1/2 z-50 px-6 py-3 rounded-full shadow-2xl flex items-center gap-3 font-bold bg-card/90 backdrop-blur-md text-theme-text border border-theme-border animate-fade-in">
      <span>{message}</span>
    </div>
  );
}
