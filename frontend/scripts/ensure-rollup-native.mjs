import { execSync } from 'node:child_process';
import { existsSync } from 'node:fs';

const platform = process.platform;
const arch = process.arch;

const nativePackages = {
  win32: {
    x64: '@rollup/rollup-win32-x64-msvc',
    arm64: '@rollup/rollup-win32-arm64-msvc',
  },
  linux: {
    x64: '@rollup/rollup-linux-x64-gnu',
    arm64: '@rollup/rollup-linux-arm64-gnu',
  },
  darwin: {
    x64: '@rollup/rollup-darwin-x64',
    arm64: '@rollup/rollup-darwin-arm64',
  },
};

const packageName = nativePackages[platform]?.[arch];

if (!packageName) {
  process.exit(0);
}

const packagePath = `node_modules/${packageName}`;

if (existsSync(packagePath)) {
  process.exit(0);
}

try {
  execSync(`npm install ${packageName} --save-dev`, { stdio: 'inherit' });
} catch {
  process.exit(0);
}
