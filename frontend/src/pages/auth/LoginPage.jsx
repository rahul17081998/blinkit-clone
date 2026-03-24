import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Eye, EyeOff } from 'lucide-react';
import toast from 'react-hot-toast';
import { authApi } from '../../api/auth.api';
import { useAuthStore } from '../../stores/authStore';
import { AuthLayout } from './AuthLayout';

const loginSchema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
});

export default function LoginPage() {
  const [showPassword, setShowPassword] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);
  const getDashboardPath = useAuthStore((s) => s.getDashboardPath);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (data) => {
    console.log('[Login] Submitting:', data.email);
    try {
      const res = await authApi.login(data);
      console.log('[Login] Response status:', res.status);
      console.log('[Login] Response data:', res.data);

      const { userId, email, role, accessToken, refreshToken } = res.data.data;
      setAuth({ userId, email, role, accessToken, refreshToken });

      toast.success('Welcome back!');

      const from = location.state?.from?.pathname;
      if (from && from !== '/login') {
        navigate(from, { replace: true });
      } else {
        if (role === 'ADMIN') navigate('/admin', { replace: true });
        else if (role === 'DELIVERY_AGENT') navigate('/agent', { replace: true });
        else navigate('/', { replace: true });
      }
    } catch (err) {
      console.error('[Login] Error:', err);
      console.error('[Login] Error response:', err.response);
      console.error('[Login] Error response data:', err.response?.data);
      console.error('[Login] Error message:', err.message);
      const msg = err.response?.data?.message || err.message || 'Login failed. Please try again.';
      toast.error(msg);
    }
  };

  return (
    <AuthLayout>
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Welcome back!</h1>
      <p className="text-gray-500 text-sm mb-6">Sign in to continue</p>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        {/* Email */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
          <input
            {...register('email')}
            type="email"
            placeholder="you@example.com"
            autoComplete="email"
            className={`input-field ${errors.email ? 'input-error' : ''}`}
          />
          {errors.email && (
            <p className="text-red-500 text-xs mt-1">{errors.email.message}</p>
          )}
        </div>

        {/* Password */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
          <div className="relative">
            <input
              {...register('password')}
              type={showPassword ? 'text' : 'password'}
              placeholder="••••••••"
              autoComplete="current-password"
              className={`input-field pr-12 ${errors.password ? 'input-error' : ''}`}
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
            >
              {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
          {errors.password && (
            <p className="text-red-500 text-xs mt-1">{errors.password.message}</p>
          )}
        </div>

        {/* Forgot password */}
        <div className="flex justify-end">
          <Link to="/forgot-password" className="text-sm text-primary font-medium hover:underline">
            Forgot Password?
          </Link>
        </div>

        {/* Submit */}
        <button type="submit" disabled={isSubmitting} className="btn-primary mt-2">
          {isSubmitting ? 'Signing in...' : 'Login'}
        </button>
      </form>

      {/* Divider */}
      <div className="flex items-center gap-3 my-5">
        <div className="flex-1 h-px bg-gray-200" />
        <span className="text-gray-400 text-sm">or</span>
        <div className="flex-1 h-px bg-gray-200" />
      </div>

      <p className="text-center text-sm text-gray-600">
        Don't have an account?{' '}
        <Link to="/signup" className="font-semibold text-gray-900 hover:underline">
          Sign Up
        </Link>
      </p>
    </AuthLayout>
  );
}
