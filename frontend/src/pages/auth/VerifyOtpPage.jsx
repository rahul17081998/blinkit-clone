import { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { authApi } from '../../api/auth.api';
import { AuthLayout } from './AuthLayout';

const OTP_LENGTH = 6;

export default function VerifyOtpPage() {
  const [otp, setOtp] = useState(Array(OTP_LENGTH).fill(''));
  const [loading, setLoading] = useState(false);
  const inputRefs = useRef([]);
  const navigate = useNavigate();
  const location = useLocation();

  const email = location.state?.email;

  // Redirect if no email in state
  useEffect(() => {
    if (!email) {
      navigate('/signup', { replace: true });
    }
  }, [email, navigate]);

  const handleChange = (index, value) => {
    if (!/^\d*$/.test(value)) return; // digits only
    const newOtp = [...otp];
    newOtp[index] = value.slice(-1); // take last char if paste
    setOtp(newOtp);

    // Auto-advance
    if (value && index < OTP_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !otp[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e) => {
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, OTP_LENGTH);
    if (pasted.length === OTP_LENGTH) {
      setOtp(pasted.split(''));
      inputRefs.current[OTP_LENGTH - 1]?.focus();
    }
  };

  const handleVerify = async () => {
    const code = otp.join('');
    if (code.length < OTP_LENGTH) {
      toast.error('Enter all 6 digits');
      return;
    }
    setLoading(true);
    try {
      await authApi.verifyOtp({ email, otp: code });
      toast.success('Email verified! Please log in.');
      navigate('/login', { replace: true, state: { verified: true } });
    } catch (err) {
      const msg = err.response?.data?.message || 'Invalid OTP. Please try again.';
      toast.error(msg);
      setOtp(Array(OTP_LENGTH).fill(''));
      inputRefs.current[0]?.focus();
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Verify your email</h1>
      <p className="text-gray-500 text-sm mb-6">
        We sent a 6-digit code to{' '}
        <span className="font-medium text-gray-800">{email}</span>
      </p>

      {/* OTP inputs */}
      <div className="flex justify-center gap-3 mb-6" onPaste={handlePaste}>
        {otp.map((digit, index) => (
          <input
            key={index}
            ref={(el) => (inputRefs.current[index] = el)}
            type="text"
            inputMode="numeric"
            maxLength={1}
            value={digit}
            onChange={(e) => handleChange(index, e.target.value)}
            onKeyDown={(e) => handleKeyDown(index, e)}
            className={`w-12 h-14 text-center text-xl font-bold border-2 rounded-xl
              focus:outline-none focus:border-primary transition-all
              ${digit ? 'border-primary bg-primary/10' : 'border-gray-200 bg-white'}`}
          />
        ))}
      </div>

      <button
        onClick={handleVerify}
        disabled={loading || otp.join('').length < OTP_LENGTH}
        className="btn-primary"
      >
        {loading ? 'Verifying...' : 'Verify Email'}
      </button>

      <p className="text-center mt-4 text-xs text-gray-400">
        OTP is valid for 5 minutes. Check your spam folder if not received.
      </p>

      <div className="text-center mt-2">
        <Link to="/signup" className="text-sm text-gray-400 hover:text-gray-600">
          ← Wrong email? Go back
        </Link>
      </div>
    </AuthLayout>
  );
}
