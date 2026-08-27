import sys
from pathlib import Path

import numpy as np
import matplotlib.pyplot as plt

def dB(x):
    return 20*np.log10(x)

def get_data(file: str) -> np.ndarray:
    with open(file) as f:
        return np.array([int(line.split(',')[0]) for line in f if line.strip(', \n')])

def plot(title: str, data: np.ndarray):
    fig, (t, f) = plt.subplots(1, 2, figsize=(12, 4))

    t.stem(data)
    t.set_title(title)
    t.set_xlabel('n')
    t.set_ylabel('x[n]')

    n = max(4096, 32*len(data))
    with np.errstate(divide='ignore'):
        mag = dB(np.abs(np.fft.rfft(data, n)))
    f.plot(np.fft.rfftfreq(n), mag)
    f.set_xlim(0, 0.5)
    finite = mag[np.isfinite(mag)]
    lo, hi = np.percentile(finite, 1), finite.max()
    pad = 0.05*(hi - lo) or 1.0
    f.set_ylim(lo - pad, hi + pad)
    f.set_xlabel('f/fs')
    f.set_ylabel('Magnitude (dB)')

    fig.tight_layout()
    return fig

if __name__ == '__main__':
    for file in sys.argv[1:]:
        fig = plot(file, get_data(file))
        fig.savefig(Path(file).with_suffix('.png'))
        plt.close(fig)
