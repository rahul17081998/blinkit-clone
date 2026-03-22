/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#F8C200',
          50: '#FFF9E0',
          100: '#FFF3C2',
          200: '#FFE785',
          300: '#FFDB47',
          400: '#F8C200',
          500: '#C89A00',
          600: '#987500',
          700: '#685100',
        },
        dark: '#0C0F0A',
        blinkit: {
          yellow: '#F8C200',
          green: '#22C55E',
          bg: '#F5F5F5',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}

