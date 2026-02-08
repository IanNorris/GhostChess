// Allow overriding webpack dev server port and host via environment variables
if (config.devServer) {
    if (process.env.WEBPACK_DEV_PORT) {
        config.devServer.port = parseInt(process.env.WEBPACK_DEV_PORT, 10);
    }
    if (process.env.WEBPACK_DEV_HOST) {
        config.devServer.host = process.env.WEBPACK_DEV_HOST;
    }
    config.devServer.open = false;
    config.devServer.allowedHosts = "all";
}
