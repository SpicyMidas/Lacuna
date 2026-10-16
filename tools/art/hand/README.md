# Hand-drawn textures

Files in this folder are drawn by hand and copied **byte for byte** over the generated textures by
`tools/gen_textures.py` (after every art module has run). Paths mirror `assets/lacuna/textures/`.
Never re-encode, resize or regenerate them; to replace one, drop the new PNG (and its `.mcmeta`, if animated)
at the same path.

`item/chronoshard_pocket_watch.png.mcmeta` lists the frames to play. The exported strip holds about one and a half
turns of the slow hand with repeated and stray frames (the hands jumped back or flashed to the wrong place, and the
loop seam snapped the slow hand back by a quarter turn), so the frame list plays exactly one full turn of the slow hand
(frames 196-515), skips the fast hand's repeats, and closes the loop where both hands line up again. The PNG itself is
untouched.
