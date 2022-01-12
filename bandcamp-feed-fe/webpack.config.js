const path = require("path");
const HtmlWebpackPlugin = require("html-webpack-plugin");

module.exports = (env) => ({
  mode: env.production ? "production" : "development",
  devtool: env.production ? undefined : "inline-source-map",
  entry: "./src/index.tsx",
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: "ts-loader",
        exclude: /node_modules/,
      },
      {
        test: /\.css$/i,
        use: ["style-loader", "css-loader", "postcss-loader"],
      },
    ],
  },
  resolve: {
    extensions: [".tsx", ".ts", ".js"],
  },
  devServer: {
    static: "./dist",
    allowedHosts: ["localhost.roitgrund.me"],
    server: {
      type: "https",
      options: {
        key: "../nginx/localhost-key.pem",
        cert: "../nginx/localhost.pem",
      },
    },
    devMiddleware: {
      index: true,
      mimeTypes: { phtml: "text/html" },
      mimeTypes: { js: "application/js" },
      writeToDisk: true,
    },
  },
  output: {
    publicPath: env.production
      ? "/static"
      : "https://localhost.roitgrund.me:8081",
    filename: "index.js",
    path: path.resolve(__dirname, "dist"),
    clean: true,
  },
  plugins: [
    new HtmlWebpackPlugin({
      template: "src/index.html",
      hash: true,
    }),
  ],
});
