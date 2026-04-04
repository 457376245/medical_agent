/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        ink: "var(--ink)",
        muted: "var(--muted)",
        line: "var(--line)",
        surface: "var(--surface)",
        "surface-strong": "var(--surface-strong)",
        "bg-neutral": "var(--bg-neutral)",
        "bg-soft-blue": "var(--bg-soft-blue)",
        primary: {
          DEFAULT: "var(--primary)",
          soft: "var(--primary-soft)",
          dark: "var(--primary-dark)",
          darker: "var(--primary-darker)",
        },
        agent: {
          DEFAULT: "var(--agent)",
          soft: "var(--agent-soft)",
          dark: "var(--agent-dark)",
          border: "var(--agent-border)",
        },
        danger: {
          DEFAULT: "var(--danger)",
          soft: "var(--danger-soft)",
          border: "var(--danger-border)",
        },
        ok: {
          DEFAULT: "var(--ok)",
          soft: "var(--ok-soft)",
          border: "var(--ok-border)",
        },
        info: {
          DEFAULT: "var(--info)",
          soft: "var(--info-soft)",
          border: "var(--info-border)",
        },
      },
      fontFamily: {
        body: ["var(--font-body)"],
        heading: ["var(--font-heading)"],
      },
      spacing: {
        "1": "4px",
        "2": "8px",
        "3": "12px",
        "4": "16px",
        "5": "20px",
        "6": "24px",
        "8": "32px",
        "10": "40px",
        "12": "48px",
      },
      borderRadius: {
        sm: "var(--radius-sm)",
        DEFAULT: "var(--radius-sm)",
        md: "var(--radius-md)",
        lg: "var(--radius-lg)",
        xl: "var(--radius-xl)",
        "2xl": "var(--radius-xl)",
        pill: "var(--radius-pill)",
      },
      boxShadow: {
        sm: "var(--shadow-sm)",
        md: "var(--shadow-md)",
        lg: "var(--shadow-lg)",
        xl: "var(--shadow-xl)",
        hover: "var(--shadow-hover)",
        menu: "var(--shadow-menu)",
        ring: "var(--ring)",
      },
      zIndex: {
        dropdown: "20",
        sticky: "30",
        "modal-backdrop": "40",
        modal: "50",
        toast: "60",
      },
    },
  },
  plugins: [],
};
