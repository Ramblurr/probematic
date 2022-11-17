const defaultTheme = require('tailwindcss/defaultTheme')
/** @type {import('tailwindcss').Config} */
module.exports = {
    content: ["./src/**/*.clj",
              "./src/**/*.cljs",
              "./resources/public/css/main.css"
             ],
    theme: {
        extend: {
            colors: {
                'red': {
                    50: '#FCF5F5',
                    100: '#F9EAEA',
                    200: '#F0CCCB',
                    300: '#E6ADAB',
                    400: '#D46F6D',
                    500: '#C1312E',
                    600: '#AE2C29',
                    700: '#741D1C',
                    800: '#571615',
                    900: '#3A0F0E',
                },
                'purple': {
                    50: '#F8F6FA',
                    100: '#F1EEF6',
                    200: '#DBD4E7',
                    300: '#C5BBD9',
                    400: '#9A87BD',
                    500: '#6F54A0',
                    600: '#644C90',
                    700: '#433260',
                    800: '#322648',
                    900: '#211930',
                },
                'violet': {
                    50: '#FAF6F9',
                    100: '#F5ECF3',
                    200: '#E7D0E1',
                    300: '#D8B4CF',
                    400: '#BA7BAB',
                    500: '#9D4387',
                    600: '#8D3C7A',
                    700: '#5E2851',
                    800: '#471E3D',
                    900: '#2F1429',
                },
                'pink': {
                    50: '#FDF5F7',
                    100: '#FAEBEF',
                    200: '#F3CED8',
                    300: '#ECB0C0',
                    400: '#DD7591',
                    500: '#CF3A62',
                    600: '#BA3458',
                    700: '#7C233B',
                    800: '#5D1A2C',
                    900: '#3E111D',
                },
                'orange': {
                    50: '#FDF9F4',
                    100: '#FCF2EA',
                    200: '#F7DFCA',
                    300: '#F3CBA9',
                    400: '#E9A569',
                    500: '#E07E29',
                    600: '#CA7125',
                    700: '#864C19',
                    800: '#653912',
                    900: '#43260C',
                },
                'yellow': {
                    50: '#FEFEF4',
                    100: '#FEFEEA',
                    200: '#FCFBC9',
                    300: '#F9F9A9',
                    400: '#F5F569',
                    500: '#F1F028',
                    600: '#D9D824',
                    700: '#919018',
                    800: '#6C6C12',
                    900: '#48480C',
                },

            },
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
