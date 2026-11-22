/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: '#0B0C10',
        card: '#12131A',
        surface: '#181924',
        accent: '#8B5CF6',
        accentHover: '#7C3AED',
        primaryText: '#FFFFFF',
        secondaryText: '#A0A3BD',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      animation: {
        'pulse-glow': 'pulseGlow 2.5s infinite ease-in-out',
        'fade-in': 'fadeIn 0.25s ease-out',
      },
      keyframes: {
        pulseGlow: {
          '0%, 100%': { opacity: '0.4' },
          '50%': { opacity: '0.85' },
        },
        fadeIn: {
          from: { opacity: '0', transform: 'translateY(6px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        }
      }
    },
  },
  plugins: [],
}
