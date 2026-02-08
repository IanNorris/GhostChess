#!/usr/bin/env bash
#
# download_originals.sh — Download original 320kbps music from OpenGameArt.
#
# These are CC0 (public domain) tracks by RandomMind.
# https://opengameart.org/users/randommind
#
# The originals are .gitignored to keep the repo small.
# Run this script before convert_music.sh if you need to re-encode.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${SCRIPT_DIR}/../assets/audio/music-originals"

mkdir -p "${OUT_DIR}"

declare -A URLS=(
    [battle]="https://opengameart.org/sites/default/files/battle_8.mp3"
    [exploration]="https://opengameart.org/sites/default/files/Exploration_0.mp3"
    [harvest-season]="https://opengameart.org/sites/default/files/harvestseason_2.mp3"
    [the-old-tower-inn]="https://opengameart.org/sites/default/files/The_Old_Tower_Inn.mp3"
    [the-bards-tale]="https://opengameart.org/sites/default/files/The_Bards_Tale.mp3"
    [market-day]="https://opengameart.org/sites/default/files/Market_Day.mp3"
    [minstrel-dance]="https://opengameart.org/sites/default/files/Minstrel_Dance_0.mp3"
    [kings-feast]="https://opengameart.org/sites/default/files/Kings_Feast_0.mp3"
    [rejoicing]="https://opengameart.org/sites/default/files/Rejoicing_0.mp3"
    [victory-theme]="https://opengameart.org/sites/default/files/victory_0.mp3"
    [defeat-theme]="https://opengameart.org/sites/default/files/defeat_0.mp3"
)

echo "Downloading 11 Medieval tracks from OpenGameArt..."
echo ""

for track in "${!URLS[@]}"; do
    url="${URLS[$track]}"
    dest="${OUT_DIR}/${track}.mp3"

    if [ -f "${dest}" ]; then
        echo "  ⏭ ${track}.mp3 (already exists)"
    else
        echo -n "  ⬇ ${track}.mp3 ... "
        curl -sL -o "${dest}" "${url}"
        size=$(du -h "${dest}" | cut -f1)
        echo "${size}"
    fi
done

echo ""
TOTAL=$(du -sh "${OUT_DIR}" | cut -f1)
echo "Done! Total: ${TOTAL} in ${OUT_DIR}"
