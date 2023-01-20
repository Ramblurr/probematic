const defaultTheme = require('tailwindcss/defaultTheme')
/** @type {import('tailwindcss').Config} */
module.exports = {
    content: {
        files: ["./src/**/*.clj",
                "./src/**/*.cljs",
                "./resources/public/js/widgets/**/*.js",
                "./resources/public/js/app.js"
               ],
    },
    theme: {
        extend: {
            fontFamily: {
                sans: ['Inter var', ...defaultTheme.fontFamily.sans],
            },
        },
    },
    variants: {
        margin: ['responsive', 'first'],
        opacity: ['responsive', 'hover', 'focus', 'disabled']
    },
    plugins: [
        require("@tailwindcss/typography"),
        require("@tailwindcss/forms"),
        require("@tailwindcss/aspect-ratio")
    ],
};
