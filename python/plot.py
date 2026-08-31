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

    t.stem(data, markerfmt='.').markerline.set_markersize(4)
    t.set_title(title)
    t.set_xlabel('n')
    t.set_ylabel('x[n]')

    n = max(4096, 32*len(data))
    Xk = np.fft.rfft(data, n)
    dc = np.abs(Xk[0])
    Xk = Xk / Xk[0]
    with np.errstate(divide='ignore'):
        mag = dB(np.abs(Xk))
        dc_dB = dB(dc)
    f.plot(np.fft.rfftfreq(n), mag)
    f.set_xlim(0, 0.5)
    finite = mag[np.isfinite(mag)]
    lo, hi = np.percentile(finite, 1), finite.max()
    # a flat response varies only by floating-point noise; don't zoom into it
    if hi - lo < 6:
        mid = (hi + lo)/2
        lo, hi = mid - 3, mid + 3
    pad = 0.05*(hi - lo)
    lo, hi = lo - pad, hi + pad
    f.set_ylim(lo, hi)
    f.set_xlabel('f/fs')
    f.set_ylabel('Normalized magnitude (dB)')
    f.axhline(0, color='0.6', lw=0.8, zorder=0)

    # anchor the ticks on 0 dB so the DC gain lands exactly on a tick
    step = next((s for s in (1, 2, 5, 10, 20, 50, 100, 200, 500)
                 if (hi - lo)/s <= 10), 500)
    ticks = np.arange(np.ceil(lo/step)*step, hi + step/2, step)
    f.set_yticks(ticks)

    if np.isfinite(dc_dB):
        true = f.twinx()
        true.set_ylim(lo, hi)
        true.set_yticks(ticks, [f'{y + dc_dB:.1f}' for y in ticks])
        true.set_ylabel('True magnitude (dB)')

    fig.tight_layout()
    return fig

if __name__ == '__main__':
    for file in sys.argv[1:]:
        fig = plot(file, get_data(file))
        fig.savefig(Path(file).with_suffix('.png'))
        plt.show()
        plt.close(fig)
