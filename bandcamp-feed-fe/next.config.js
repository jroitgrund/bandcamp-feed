/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  assetPrefix: process.env.NODE_ENV === "production" ? "/static" : "",
};

module.exports = nextConfig;
