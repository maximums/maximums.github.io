#!/bin/sh
# Masters the rain the site plays (composeApp/src/wasmJsMain/resources/sounds) from Freesound's HQ preview of
# "Light rain on street" by BonnyOrbit (CC0): https://freesound.org/s/380651/
#
# Needs ffmpeg with libopus (6.1 on Ubuntu 24.04 works). From the repository root:
#   sh tools/sounds/master-rain.sh [source]      (without a source, the preview is downloaded)
#
# - A high-pass at 30 Hz. Below it is rumble no speaker the page plays on reproduces; the passing cars sit above.
# - A plain gain to TARGET LUFS (EBU R128 integrated loudness). The recording is steady (a loudness range of 0.8 LU)
#   and its peaks land near -4 dBFS, so nothing needs limiting. The player adds no correction of its own.
# - Opus at 80 kbps in WebM for every browser that decodes it, AAC at 128 kbps for the ones that don't (older
#   Safari). The player tries them in that order.
set -eu

TARGET=-30
out=composeApp/src/wasmJsMain/resources/sounds

source=${1:-}
if [ -z "$source" ]; then
    source=$(mktemp --suffix=.mp3)
    curl -sSfL -o "$source" https://cdn.freesound.org/previews/380/380651_5902878-hq.mp3
fi

loudness() { # integrated loudness of $1 after the filters in $2
    ffmpeg -hide_banner -nostats -i "$1" -af "$2,ebur128" -f null - 2>&1 | awk '/^ +I:/ { v = $2 } END { print v }'
}

measured=$(loudness "$source" highpass=f=30)
gain=$(awk -v t="$TARGET" -v m="$measured" 'BEGIN { printf "%.2f", t - m }')
filters="highpass=f=30,volume=${gain}dB"
echo "source at $measured LUFS: ${gain} dB of gain to reach $TARGET"

ffmpeg -hide_banner -loglevel error -y -i "$source" -af "$filters" -map_metadata -1 \
    -c:a libopus -b:a 80k -vbr on "$out/rain.webm"
ffmpeg -hide_banner -loglevel error -y -i "$source" -af "$filters" -map_metadata -1 \
    -c:a aac -b:a 128k -movflags +faststart "$out/rain.m4a"

for file in "$out/rain.webm" "$out/rain.m4a"; do
    echo "$file: $(loudness "$file" anull) LUFS, $(wc -c < "$file") bytes"
done
