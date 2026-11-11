"""Original synthetic activation cues. Build-time only: numpy + soundfile 0.13.1, no runtime mod dependency."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".tools" / "audio-libs"))
import numpy as np
import soundfile as sf

destination = ROOT / "src/main/resources/assets/legacyoftheforce/sounds/saber"
destination.mkdir(parents=True, exist_ok=True)
sample_rate = 22050
rng = np.random.default_rng(737)
for name, duration, start, end in [("ignite", 0.42, 150, 620), ("extinguish", 0.34, 540, 95)]:
    t = np.arange(round(duration * sample_rate)) / sample_rate
    progress = t / duration
    frequency = start + (end - start) * progress
    phase = 2 * np.pi * np.cumsum(frequency) / sample_rate
    envelope = np.sin(np.pi * progress) ** 1.4
    signal = envelope * (0.5 * np.sin(phase) + 0.16 * np.sin(phase * 2.01) + 0.06 * rng.uniform(-1, 1, t.size))
    path = destination / f"{name}.ogg"
    sf.write(path, signal, sample_rate, format="OGG", subtype="VORBIS")
    decoded, rate = sf.read(path)
    assert rate == sample_rate and decoded.ndim == 1 and decoded.size == t.size
    assert np.isfinite(decoded).all() and np.max(np.abs(decoded)) <= 1 and path.stat().st_size > 100
    print(f"{name}: {decoded.size} mono samples, {rate} Hz, verified OGG/Vorbis")
