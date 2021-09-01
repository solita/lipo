module.exports = {
    purge: {
        enabled: true,
        content: ['./src/**/*.clj', './resources/**/*.html'],
        extract: {
            md: (content) => {
                let m = content.match(/\.[^.]+/g);
                let results = [];
                if (m != null) m.map((s) => results.push(s.substr(1)));
                m = content.match(/[^<>"'`\s]*[^<>"'`\s:]/g);
                if (m != null) m.map((s) => results.push(s.substr(1)));
                return results;
            },
        },
    },
    darkMode: false, // or 'media' or 'class'
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
    plugins: [],
};
