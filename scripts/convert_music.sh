#!/usr/bin/env bash
#
# convert_music.sh — Convert original music assets to platform-specific formats.
#
# Original 320kbps stereo MP3s live in assets/audio/music-originals/.
# This script encodes them for each target platform and copies to the
# correct resource directories.
#
# Usage:
#   ./scripts/convert_music.sh [profile]
#
# Profiles:
#   mobile   — 48 kbps mono MP3 (default, ~8 MB total)
#   low      — 32 kbps mono MP3 (~5 MB total)
#   desktop  — 128 kbps stereo MP3 (~22 MB total)
#   hifi     — 320 kbps stereo MP3, copies originals (~47 MB total)
#   custom   — set BITRATE, CHANNELS, CODEC env vars
#
# Examples:
#   ./scripts/convert_music.sh mobile
#   ./scripts/convert_music.sh desktop
#   BITRATE=96 CHANNELS=1 ./scripts/convert_music.sh custom
#
# Requirements: ffmpeg
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ORIGINALS_DIR="${PROJECT_ROOT}/assets/audio/music-originals"
WEB_AUDIO_DIR="${PROJECT_ROOT}/webApp/src/jsMain/resources/audio"
ANDROID_RAW_DIR="${PROJECT_ROOT}/composeApp/src/androidMain/res/raw"

PROFILE="${1:-mobile}"

# Track list (filename stems matching originals)
TRACKS=(
    battle
    exploration
    harvest-season
    the-old-tower-inn
    the-bards-tale
    market-day
    minstrel-dance
    kings-feast
    rejoicing
    victory-theme
    defeat-theme
)

# Configure encoding based on profile
case "${PROFILE}" in
    mobile)
        BITRATE=48
        CHANNELS=1
        CODEC=mp3
        EXT=mp3
        ;;
    low)
        BITRATE=32
        CHANNELS=1
        CODEC=mp3
        EXT=mp3
        ;;
    desktop)
        BITRATE=128
        CHANNELS=2
        CODEC=mp3
        EXT=mp3
        ;;
    hifi)
        BITRATE=320
        CHANNELS=2
        CODEC=mp3
        EXT=mp3
        ;;
    custom)
        BITRATE="${BITRATE:?Set BITRATE env var (e.g. 96)}"
        CHANNELS="${CHANNELS:-1}"
        CODEC="${CODEC:-mp3}"
        EXT="${CODEC}"
        ;;
    *)
        echo "Unknown profile: ${PROFILE}"
        echo "Usage: $0 [mobile|low|desktop|hifi|custom]"
        exit 1
        ;;
esac

echo "╔══════════════════════════════════════════════╗"
echo "║  Music Converter — Profile: ${PROFILE}"
echo "║  Bitrate: ${BITRATE}k | Channels: ${CHANNELS} | Codec: ${CODEC}"
echo "╚══════════════════════════════════════════════╝"
echo ""

# Verify ffmpeg is available
if ! command -v ffmpeg &>/dev/null; then
    echo "Error: ffmpeg is required but not found."
    exit 1
fi

# Verify originals exist
if [ ! -d "${ORIGINALS_DIR}" ]; then
    echo "Error: Originals directory not found: ${ORIGINALS_DIR}"
    echo "Place original 320kbps MP3s in assets/audio/music-originals/"
    exit 1
fi

# Clean existing music files from output dirs
echo "Cleaning existing music files..."
rm -f "${WEB_AUDIO_DIR}"/music-medieval-*.mp3 "${WEB_AUDIO_DIR}"/music-medieval-*.ogg
rm -f "${ANDROID_RAW_DIR}"/music_medieval_*.mp3 "${ANDROID_RAW_DIR}"/music_medieval_*.ogg

TOTAL_SIZE=0
CONVERTED=0

for track in "${TRACKS[@]}"; do
    src="${ORIGINALS_DIR}/${track}.mp3"
    if [ ! -f "${src}" ]; then
        echo "  ⚠ Skipping ${track} — original not found"
        continue
    fi

    # Web output: music-medieval-{track}.{ext}
    web_out="${WEB_AUDIO_DIR}/music-medieval-${track}.${EXT}"

    # Android output: music_medieval_{track}.{ext} (hyphens → underscores)
    android_name="music_medieval_$(echo "${track}" | tr '-' '_').${EXT}"
    android_out="${ANDROID_RAW_DIR}/${android_name}"

    if [ "${PROFILE}" = "hifi" ]; then
        # Just copy originals
        cp "${src}" "${web_out}"
        cp "${src}" "${android_out}"
    else
        # Encode with ffmpeg
        ffmpeg -nostdin -y -i "${src}" \
            -c:a "${CODEC}" -b:a "${BITRATE}k" -ac "${CHANNELS}" \
            "${web_out}" 2>/dev/null

        cp "${web_out}" "${android_out}"
    fi

    size=$(stat -c%s "${web_out}" 2>/dev/null || stat -f%z "${web_out}" 2>/dev/null)
    size_kb=$((size / 1024))
    TOTAL_SIZE=$((TOTAL_SIZE + size))
    CONVERTED=$((CONVERTED + 1))

    echo "  ✓ ${track} → ${size_kb} KB"
done

TOTAL_MB=$(echo "scale=1; ${TOTAL_SIZE}/1048576" | bc)

echo ""
echo "Done! ${CONVERTED} tracks converted."
echo "Total size per platform: ${TOTAL_MB} MB"
echo ""
echo "Output locations:"
echo "  Web:     ${WEB_AUDIO_DIR}/music-medieval-*"
echo "  Android: ${ANDROID_RAW_DIR}/music_medieval_*"
