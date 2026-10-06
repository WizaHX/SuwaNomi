#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir/suwa-server"
./gradlew :server:clean :server:shadowJar --no-daemon --max-workers=2 \
    -Dorg.gradle.jvmargs=-Xmx3g -Pkotlin.compiler.execution.strategy=in-process
set -- server/build/*.jar
[ "$#" -eq 1 ] && [ -f "$1" ] || { echo 'Expected one server JAR' >&2; exit 1; }
mkdir -p "$project_dir/.docker-build"
cp "$1" "$project_dir/.docker-build/server.jar"

cd "$project_dir/suwa-webUI"
HUSKY=0 pnpm install --frozen-lockfile
pnpm exec tsx tools/scripts/setupEnvFiles.ts
pnpm exec vite build
rm -rf "$project_dir/.docker-build/webui"
cp -R build "$project_dir/.docker-build/webui"
printf 'r%s\n' "$(git rev-list HEAD --count)" > "$project_dir/.docker-build/webui/revision"
