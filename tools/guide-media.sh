#!/bin/sh
# Turns the demo reel's clips (demo/clips/*.mp4, from -PdemoReelOnly=true) into the guide's media in docs/videos/:
# a compressed MP4 per clip, a short looping GIF preview of each, and the full tour.
set -eu
FFMPEG=${FFMPEG:-ffmpeg}
FFPROBE=${FFPROBE:-ffprobe}
SRC=${1:-demo}
OUT=${2:-docs/videos}
mkdir -p "$OUT"
for clip in "$SRC"/clips/[0-9]*.mp4; do
    name=$(basename "$clip" .mp4)
    "$FFMPEG" -y -loglevel error -i "$clip" -c:v libx264 -preset slow -crf 24 -pix_fmt yuv420p -movflags +faststart "$OUT/$name.mp4"
    # Preview: up to 6 seconds from a third of the way in, 480 px wide, 10 fps, one shared palette.
    duration=$("$FFPROBE" -v error -show_entries format=duration -of csv=p=0 "$clip")
    start=$(awk -v d="$duration" 'BEGIN { s = d / 3; if (s + 6 > d) s = (d > 6 ? d - 6 : 0); printf "%.2f", s }')
    "$FFMPEG" -y -loglevel error -ss "$start" -t 6 -i "$clip" \
        -vf "fps=10,scale=480:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=4" \
        -loop 0 "$OUT/$name.gif"
    echo "$name: $(du -h "$OUT/$name.mp4" | cut -f1) mp4, $(du -h "$OUT/$name.gif" | cut -f1) gif"
done
"$FFMPEG" -y -loglevel error -i "$SRC/donut-client-demo.mp4" -c:v libx264 -preset slow -crf 24 -pix_fmt yuv420p -movflags +faststart "$OUT/donut-client-demo.mp4"
echo "full tour: $(du -h "$OUT/donut-client-demo.mp4" | cut -f1)"
