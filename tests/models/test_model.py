"""Offline, desktop integration smoke test; not an acoustic accuracy benchmark."""
from pathlib import Path
import hashlib
import wave
import numpy as np
from ai_edge_litert.interpreter import Interpreter

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / 'app/src/main/assets/models/yamnet.tflite'
assert hashlib.sha256(MODEL.read_bytes()).hexdigest() == '10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de'
labels = (MODEL.parent / 'yamnet-labels.txt').read_text().splitlines()
assert len(labels) == 521
assert labels[0] == 'Speech' and labels[390] == 'Siren' and labels[277] == 'Wind' and labels[294] == 'Vehicle'
model = Interpreter(model_path=str(MODEL), num_threads=1)
model.allocate_tensors()
inp, out = model.get_input_details()[0], model.get_output_details()[0]
assert tuple(inp['shape']) == (15600,) and tuple(out['shape']) == (1, 521)
def infer(audio):
    model.set_tensor(inp['index'], audio.astype(np.float32))
    model.invoke()
    scores = model.get_tensor(out['index'])[0]
    assert np.isfinite(scores).all() and (scores >= 0).all() and (scores <= 1).all()
    return scores
silence = infer(np.zeros(15600))
assert silence[labels.index('Silence')] > .5 and silence[0] < .1
wav_path = Path(__file__).with_name('speech.wav')
assert hashlib.sha256(wav_path.read_bytes()).hexdigest() == '219f55927c62af71fa7dc29581b647ade375286498dd0e342cc07c72cc8edc04'
with wave.open(str(wav_path)) as wav:
    assert wav.getframerate() == 16000 and wav.getnchannels() == 1 and wav.getsampwidth() == 2
    pcm = np.frombuffer(wav.readframes(15600), dtype='<i2').astype(np.float32) / 32768
speech = infer(pcm)
assert speech[0] > .5 and speech.argmax() == 0
print(f'PASS: pinned model/vocabulary, tensor shapes, silence {silence[labels.index("Silence")]:.3f}, speech {speech[0]:.3f}')
print('This does not establish accuracy for vehicles, wind, sirens, mixtures, or phone microphones.')
