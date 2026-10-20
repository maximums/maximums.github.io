#!/bin/sh
# Prepares the maple bonsai the site draws on Home (composeApp/src/commonMain/composeResources/files/models) from
# Sketchfab's download of "Maple Bonsai Tree" by NPHoa_Kagoby (primagenevieve), CC BY 4.0:
# https://sketchfab.com/3d-models/maple-bonsai-tree-1710f5553283441ea2b041e4738314ec
#
# Sketchfab's downloads need an account, so the source is a file: the GLB download (maple_bonsai_tree.glb).
# Needs Node.js; npx fetches glTF Transform at the version pinned below. From the repository root:
#   sh tools/models/prepare-maple.sh path/to/maple_bonsai_tree.glb
#
# The download is 17 MB, and 15 MB of it is one maple leaf written out 958 times, each copy in its own mesh.
# - dedup: identical accessors and meshes merged. 960 meshes become 3 (the trunk, the pot, the leaf), the leaf shared
#   by 958 nodes.
# - instance: those 958 nodes become one node with EXT_mesh_gpu_instancing, a translation, rotation and scale per
#   leaf, so the leaves are one instanced draw.
# - prune: whatever nothing uses any more.
# Nothing is approximated: every leaf lands where it was (the reader's FixtureTest checks the shipped file's counts and
# bounds against the download's). The textures stay as they came: three 1024 px PNGs, 0.4 MB. About 0.5 MB in all.
set -eu

GLTF_TRANSFORM="npx -y @gltf-transform/cli@4.5.1"
out=composeApp/src/commonMain/composeResources/files/models/maple-bonsai.glb

source=${1:?"usage: sh tools/models/prepare-maple.sh path/to/maple_bonsai_tree.glb"}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

$GLTF_TRANSFORM dedup "$source" "$work/dedup.glb"
$GLTF_TRANSFORM instance "$work/dedup.glb" "$work/instanced.glb"
mkdir -p "$(dirname "$out")"
$GLTF_TRANSFORM prune "$work/instanced.glb" "$out"

# The validator knows no EXT_mesh_gpu_instancing, so it reports the instances' accessors as possibly unused (info).
$GLTF_TRANSFORM validate "$out"
echo "$out: $(wc -c < "$out") bytes"
