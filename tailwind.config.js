module.exports = {
    content: ['./src/**/*.clj', './resources/**/*.html'],

    theme: {
        extend: {
            colors: {
                primary: "#146a8e"
            },
        },
    },
    variants: {
        extend: {},
    },
    plugins: [require("daisyui")],
};
