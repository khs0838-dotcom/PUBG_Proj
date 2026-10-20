#!/usr/bin/env python3
"""Convert OWNED/authorized 16-bit PNG height raster to mobile HMAP format.
This DOES NOT determine elevation in metres or register the heightmap to a map image.
Both files must already cover the exact same geographic extent and orientation.
"""
from pathlib import Path
import argparse
import struct
import numpy as np
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('input', type=Path, help='grayscale 16-bit PNG')
p.add_argument('output', type=Path, help='output .hmap')
p.add_argument('--size', type=int, default=1024, help='square output raster side (default 1024)')
a = p.parse_args()
if not 128 <= a.size <= 2048:
    p.error('size must be 128...2048')
im = Image.open(a.input)
pixels = np.asarray(im)
if pixels.ndim != 2 or pixels.dtype != np.uint16:
    p.error(f'Expected 16-bit grayscale PNG, got shape={pixels.shape}, dtype={pixels.dtype}')
# Uniform full-image decimation; nearest preserves exact underlying raw U16 samples.
im = im.resize((a.size, a.size), Image.Resampling.NEAREST)
pixels = np.asarray(im).astype('<u2', copy=False)
with a.output.open('wb') as out:
    out.write(b'HMAP' + struct.pack('<II', a.size, a.size))
    out.write(pixels.tobytes(order='C'))
print(f'Created {a.output} ({pixels.shape}, {a.output.stat().st_size} bytes)')
print('WARNING: still need registration and raw-to-metre calibration!')
