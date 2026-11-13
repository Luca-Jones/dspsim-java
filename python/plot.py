import argparse
from pathlib import Path

import numpy as np
import matplotlib.pyplot as plt

def dB(x):
    return 20*np.log10(x)

def get_data(file: str) -> np.ndarray:
    with open(file) as f:
        # samples are arbitrary-precision BigIntegers; values past int64 would
        # make an object array that numpy's FFT rejects, so plot as float
        return np.array([int(line.split(',')[0]) for line in f if line.strip(', \n')], dtype=float)

def hertz_unit(hz: float) -> tuple[float, str]:
    """The largest of Hz, kHz, MHz and GHz that hz reaches, as (Hz per unit, name)."""
    for scale, unit in ((1e9, 'GHz'), (1e6, 'MHz'), (1e3, 'kHz')):
        if hz >= scale:
            return scale, unit
    return 1, 'Hz'

def plot(title: str, data: np.ndarray, fs: float | None = None):
    """fs is this signal's own sample rate in Hz. The frequency axis is always
    normalized to f/fs; with fs, a second axis along the top reads in Hz."""
    fig, (t, f) = plt.subplots(1, 2, figsize=(12, 4))

    t.stem(data, markerfmt='.').markerline.set_markersize(4)
    if fs is not None:
        scale, unit = hertz_unit(fs)
    t.set_title(title)
    t.set_xlabel('n')
    t.set_ylabel('x[n]')

    n = max(4096, 32*len(data))
    Xk = np.abs(np.fft.rfft(data, n))
    # 0 dB is the DC gain; a zero-mean signal (e.g. a sine) has none, so
    # normalize to its peak instead
    peak = Xk.max()
    if peak == 0:
        f.text(0.5, 0.5, 'all samples are zero', ha='center', va='center', transform=f.transAxes)
        fig.tight_layout()
        return fig
    has_dc = Xk[0] > 1e-9*peak
    ref = Xk[0] if has_dc else peak
    with np.errstate(divide='ignore'):
        mag = dB(Xk/ref)
        dc_dB = dB(ref)
    f.plot(np.fft.rfftfreq(n), mag)
    f.set_xlim(0, 0.5)
    f.set_xlabel('f/fs')
    if fs is not None:
        # one unit for the whole axis, sized to its far end (Nyquist)
        scale, unit = hertz_unit(fs/2)
        per = fs/scale
        f.secondary_xaxis('top', functions=(lambda x: x*per, lambda x: x/per)).set_xlabel(f'f ({unit})')
    finite = mag[np.isfinite(mag)]
    lo, hi = np.percentile(finite, 1), finite.max()
    # a flat response varies only by floating-point noise; don't zoom into it
    if hi - lo < 6:
        mid = (hi + lo)/2
        lo, hi = mid - 3, mid + 3
    pad = 0.05*(hi - lo)
    lo, hi = lo - pad, hi + pad
    f.set_ylim(lo, hi)
    f.set_ylabel('Magnitude re ' + ('DC' if has_dc else 'peak') + ' (dB)')
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
    ap = argparse.ArgumentParser(description='Plot dataout CSVs in time and frequency.')
    ap.add_argument('files', nargs='+', metavar='file.csv')
    ap.add_argument('--fs', nargs='+', type=float, metavar='HZ', default=[],
                    help='sample rate in Hz, one for every file or one per file in order, '
                         'given after the files; adds a frequency axis in Hz above the f/fs one')
    args = ap.parse_args()
    if any(fs <= 0 for fs in args.fs):
        ap.error('--fs must be positive')
    if len(args.fs) not in (0, 1, len(args.files)):
        ap.error(f'--fs takes 1 rate or one per file ({len(args.files)}), got {len(args.fs)}')
    rates = args.fs*len(args.files) if len(args.fs) == 1 else args.fs or [None]*len(args.files)
    for file, fs in zip(args.files, rates):
        plot(file, get_data(file), fs).savefig(Path(file).with_suffix('.png'))
    plt.show()  # once, after all figures exist: show() blocks until its windows close
