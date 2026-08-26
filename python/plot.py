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

    freqs = np.fft.rfftfreq(len(data))
    with np.errstate(divide='ignore'):
        f.plot(freqs, dB(np.abs(np.fft.rfft(data))))
    f.set_xlim(0, 0.5)
    f.set_xlabel('f/fs')
    f.set_ylabel('Magnitude (dB)')

    fig.tight_layout()
    return fig

if __name__ == '__main__':
    for file in sys.argv[1:]:
        fig = plot(file, get_data(file))
        fig.savefig(Path(file).with_suffix('.png'))
        plt.close(fig)
