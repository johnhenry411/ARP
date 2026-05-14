/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        cyber: {
          bg:       '#050D1A',
          sidebar:  '#060E1C',
          toolbar:  '#060E1C',
          card:     '#0A1628',
          hover:    '#0E1E38',
          selected: '#0B1F3A',
          border:   '#0F2744',
          cyan:     '#00D9FF',
          purple:   '#7B2FFF',
          red:      '#FF2D55',
          amber:    '#FFB800',
          green:    '#00FF9C',
          text:     '#E0F4FF',
          muted:    '#4A7FA5',
          dim:      '#1E4060',
        },
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', '"Fira Code"', 'Consolas', 'monospace'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        'glow-cyan':   '0 0 12px rgba(0,217,255,0.4)',
        'glow-purple': '0 0 12px rgba(123,47,255,0.4)',
        'glow-red':    '0 0 12px rgba(255,45,85,0.4)',
        'glow-amber':  '0 0 12px rgba(255,184,0,0.4)',
        'glow-green':  '0 0 12px rgba(0,255,156,0.4)',
      },
      animation: {
        'pulse-ring':  'pulse-ring 1.5s ease-out infinite',
        'scan-badge':  'scan-badge 1s ease-in-out infinite',
        'flicker':     'flicker 4s ease-in-out infinite',
        'slide-scan':  'slide-scan 3s linear infinite',
      },
      keyframes: {
        'pulse-ring': {
          '0%':   { transform: 'scale(1)',    opacity: '1' },
          '100%': { transform: 'scale(1.6)',  opacity: '0' },
        },
        'scan-badge': {
          '0%, 100%': { transform: 'scale(1)',    opacity: '1' },
          '50%':      { transform: 'scale(1.15)', opacity: '0.85' },
        },
        'flicker': {
          '0%, 95%, 100%': { opacity: '1' },
          '97%':           { opacity: '0.92' },
        },
        'slide-scan': {
          '0%':   { transform: 'translateY(-100%)' },
          '100%': { transform: 'translateY(100vh)' },
        },
      },
    },
  },
  plugins: [],
}
