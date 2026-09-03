/**
 * Tailwind build config. Generates a static stylesheet so production no longer
 * compiles CSS in the browser via the Play CDN (which froze mobile page loads).
 *
 * Regenerate after changing templates:
 *   npx -y tailwindcss@3 -c tailwind.config.js -i tailwind-input.css \
 *       -o src/main/resources/static/css/app.tw.css --minify
 */
module.exports = {
  darkMode: 'class',
  content: [
    './src/main/resources/templates/**/*.html',
    './src/main/java/**/*.java', // some utility classes are injected from Java (e.g. note cross-links)
  ],
  theme: { extend: {} },
  plugins: [],
};
