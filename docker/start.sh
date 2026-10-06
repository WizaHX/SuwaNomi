#!/bin/sh
set -eu

data_dir=/home/suwayomi/.local/share/Tachidesk

# This directory contains generated UI assets only; refresh them on image upgrades.
mkdir -p "$data_dir"
rm -rf "$data_dir/webUI"
mkdir -p "$data_dir/webUI"
cp -R /opt/suwanomi/webui/. "$data_dir/webUI/"

# Keep the matching client instead of downloading an upstream UI over it.
export WEB_UI_FLAVOR=Custom
export WEB_UI_UPDATE_INTERVAL=0
exec /home/suwayomi/startup_script.sh
