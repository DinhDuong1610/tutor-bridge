import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The API client ships TypeScript source from the workspace.
  transpilePackages: ["@tutorbridge/api-client"],
  cacheComponents: true,
  partialPrefetching: true,
  reactCompiler: true,
};

export default nextConfig;
