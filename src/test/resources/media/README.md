# WebP validation fixtures

These synthetic 2 × 2 images contain no personal data. They were generated with
Pillow's WebP encoder to exercise independent lossy (`VP8`), lossless (`VP8L`),
extended alpha (`VP8X` with raw `ALPH`), and animated (`ANMF`) inputs. Pillow is
not required to run the tests.

The RGB color is `(80, 120, 160)`; the alpha fixture adds alpha `128`. The animated
fixture has two lossless RGB frames, with the second color `(160, 120, 80)`, a
100 ms duration, and infinite looping. Corrupted variants are derived in tests
so that the exact damage is visible alongside each assertion.
