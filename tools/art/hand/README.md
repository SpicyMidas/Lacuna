# Hand-drawn textures

Files in this folder are drawn by hand and copied **byte for byte** over the generated textures by
`tools/gen_textures.py` (after every art module has run). Paths mirror `assets/lacuna/textures/`.
Never re-encode, resize or regenerate them; to replace one, drop the new PNG (and its `.mcmeta`, if animated)
at the same path.

`item/chronoshard_pocket_watch.png.mcmeta` lists the frames to play: the exported strip contains repeated and stray
frames (the hands jumped back or flashed to the wrong place), so the frame list keeps the smooth, always-forward
sequence of the hand-drawn frames. The PNG itself is untouched.
