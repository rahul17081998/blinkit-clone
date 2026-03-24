export function AuthLayout({ children }) {
  return (
    <div className="min-h-screen bg-gradient-to-b from-primary/20 to-blinkit-bg flex flex-col items-center justify-center px-4 py-8">
      {/* Logo */}
      <div className="mb-8 text-center">
        <div className="inline-flex items-center gap-2">
          <div className="w-10 h-10 bg-primary rounded-xl flex items-center justify-center shadow-md">
            <span className="text-dark font-black text-xl">B</span>
          </div>
          <span className="text-2xl font-black text-dark tracking-tight">blinkit</span>
        </div>
        <p className="text-sm text-gray-500 mt-1">Delivery in 10 minutes</p>
      </div>

      {/* Card */}
      <div className="w-full max-w-md bg-white rounded-3xl shadow-lg p-6 sm:p-8">
        {children}
      </div>
    </div>
  );
}
