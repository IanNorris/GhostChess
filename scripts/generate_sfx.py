#!/usr/bin/env python3
"""Generate chess sound effects as MP3 files. All output is CC0/public domain."""

import numpy as np
from scipy.io import wavfile
import subprocess
import os

SAMPLE_RATE = 44100
OUTPUT_DIR = "/home/ian/chess/webApp/src/jsMain/resources/audio"

def normalize(signal, peak=0.8):
    mx = np.max(np.abs(signal))
    if mx > 0:
        signal = signal * (peak / mx)
    return signal

def fade(signal, fade_in_ms=5, fade_out_ms=50):
    fade_in = int(SAMPLE_RATE * fade_in_ms / 1000)
    fade_out = int(SAMPLE_RATE * fade_out_ms / 1000)
    if fade_in > 0 and fade_in < len(signal):
        signal[:fade_in] *= np.linspace(0, 1, fade_in)
    if fade_out > 0 and fade_out < len(signal):
        signal[-fade_out:] *= np.linspace(1, 0, fade_out)
    return signal

def save_mp3(signal, name):
    wav_path = os.path.join(OUTPUT_DIR, f"{name}.wav")
    mp3_path = os.path.join(OUTPUT_DIR, f"{name}.mp3")
    data = (signal * 32767).astype(np.int16)
    wavfile.write(wav_path, SAMPLE_RATE, data)
    subprocess.run([
        "ffmpeg", "-y", "-i", wav_path, "-b:a", "128k", "-ar", "44100", mp3_path
    ], capture_output=True)
    os.remove(wav_path)
    print(f"  ✓ {name}.mp3 ({os.path.getsize(mp3_path)} bytes)")

def tone(freq, duration_ms, volume=1.0):
    t = np.linspace(0, duration_ms / 1000, int(SAMPLE_RATE * duration_ms / 1000), False)
    return np.sin(2 * np.pi * freq * t) * volume

def noise(duration_ms, volume=0.3):
    n = int(SAMPLE_RATE * duration_ms / 1000)
    return np.random.randn(n) * volume

# --- Sound Effects ---

def gen_move():
    """Soft wooden tap — piece placed on board."""
    n = noise(80, 0.6)
    t = tone(220, 80, 0.4)
    signal = n + t
    signal = fade(signal, 2, 40)
    save_mp3(normalize(signal, 0.5), "move")

def gen_capture():
    """Sharper impact — piece captured."""
    n = noise(50, 0.8)
    t1 = tone(330, 120, 0.5)
    t2 = tone(165, 120, 0.3)
    # Combine: sharp initial noise + dual tone
    padded_noise = np.concatenate([n, np.zeros(int(SAMPLE_RATE * 70 / 1000))])
    combined = padded_noise[:len(t1)] + t1 + t2
    signal = fade(combined, 2, 60)
    save_mp3(normalize(signal, 0.6), "capture")

def gen_check():
    """Alert tone — ascending two-note."""
    t1 = tone(523, 100, 0.6)  # C5
    gap = np.zeros(int(SAMPLE_RATE * 30 / 1000))
    t2 = tone(659, 120, 0.6)  # E5
    signal = np.concatenate([t1, gap, t2])
    signal = fade(signal, 5, 60)
    save_mp3(normalize(signal, 0.5), "check")

def gen_checkmate():
    """Victorious fanfare — three ascending notes."""
    t1 = tone(523, 150, 0.6)  # C5
    t2 = tone(659, 150, 0.6)  # E5
    t3 = tone(784, 300, 0.7)  # G5
    gap = np.zeros(int(SAMPLE_RATE * 40 / 1000))
    signal = np.concatenate([t1, gap, t2, gap, t3])
    signal = fade(signal, 5, 100)
    save_mp3(normalize(signal, 0.6), "checkmate")

def gen_castle():
    """Double tap — two piece movements."""
    tap1 = noise(60, 0.5) + tone(200, 60, 0.3)
    gap = np.zeros(int(SAMPLE_RATE * 100 / 1000))
    tap2 = noise(60, 0.5) + tone(240, 60, 0.3)
    signal = np.concatenate([fade(tap1, 2, 30), gap, fade(tap2, 2, 30)])
    save_mp3(normalize(signal, 0.5), "castle")

def gen_illegal():
    """Low buzz — invalid move."""
    t = tone(150, 200, 0.5) + tone(155, 200, 0.3)  # Slight detuning for buzz
    signal = fade(t, 5, 80)
    save_mp3(normalize(signal, 0.35), "illegal")

def gen_undo():
    """Descending two-note — reversal."""
    t1 = tone(440, 80, 0.5)   # A4
    gap = np.zeros(int(SAMPLE_RATE * 30 / 1000))
    t2 = tone(330, 100, 0.5)  # E4
    signal = np.concatenate([t1, gap, t2])
    signal = fade(signal, 5, 50)
    save_mp3(normalize(signal, 0.4), "undo")

def gen_game_start():
    """Gentle rising arpeggio — game beginning."""
    notes = [262, 330, 392, 523]  # C4, E4, G4, C5
    parts = []
    for i, freq in enumerate(notes):
        t = tone(freq, 120, 0.5 + i * 0.05)
        t = fade(t, 3, 30)
        parts.append(t)
        if i < len(notes) - 1:
            parts.append(np.zeros(int(SAMPLE_RATE * 50 / 1000)))
    signal = np.concatenate(parts)
    signal = fade(signal, 5, 80)
    save_mp3(normalize(signal, 0.45), "game-start")

def gen_draw():
    """Neutral ending — flat resolution."""
    t1 = tone(392, 200, 0.5)  # G4
    t2 = tone(349, 300, 0.5)  # F4
    gap = np.zeros(int(SAMPLE_RATE * 50 / 1000))
    signal = np.concatenate([t1, gap, t2])
    signal = fade(signal, 5, 120)
    save_mp3(normalize(signal, 0.4), "draw")

# --- Background Music ---

def gen_music_calm():
    """Calm ambient loop — gentle pad with slow chord progression (~30 seconds)."""
    duration = 30.0
    t = np.linspace(0, duration, int(SAMPLE_RATE * duration), False)
    
    # Slow chord progression: Am -> F -> C -> G (each ~7.5 seconds)
    chords = [
        (220, 262, 330),     # Am: A3, C4, E4
        (175, 220, 262),     # F: F3, A3, C4
        (131, 165, 196),     # C: C3, E3, G3
        (196, 247, 294),     # G: G3, B3, D4
    ]
    
    signal = np.zeros_like(t)
    chord_len = duration / len(chords)
    
    for i, (f1, f2, f3) in enumerate(chords):
        start = int(i * chord_len * SAMPLE_RATE)
        end = int((i + 1) * chord_len * SAMPLE_RATE)
        seg_t = t[start:end] - t[start]
        seg_len = len(seg_t)
        
        # Warm pad tones with slight detune for richness
        pad = (np.sin(2 * np.pi * f1 * seg_t) * 0.25 +
               np.sin(2 * np.pi * (f1 * 1.002) * seg_t) * 0.15 +
               np.sin(2 * np.pi * f2 * seg_t) * 0.20 +
               np.sin(2 * np.pi * (f2 * 0.998) * seg_t) * 0.12 +
               np.sin(2 * np.pi * f3 * seg_t) * 0.15 +
               np.sin(2 * np.pi * (f3 * 1.003) * seg_t) * 0.08)
        
        # Crossfade between chords
        crossfade = int(SAMPLE_RATE * 0.8)
        envelope = np.ones(seg_len)
        if crossfade < seg_len:
            envelope[:crossfade] = np.linspace(0, 1, crossfade)
            envelope[-crossfade:] = np.linspace(1, 0, crossfade)
        
        signal[start:end] += pad * envelope
    
    # Add very subtle high shimmer
    shimmer = np.sin(2 * np.pi * 880 * t) * 0.02 * (1 + np.sin(2 * np.pi * 0.1 * t)) * 0.5
    signal += shimmer
    
    # Gentle loop fade
    loop_fade = int(SAMPLE_RATE * 2)
    signal[:loop_fade] *= np.linspace(0, 1, loop_fade)
    signal[-loop_fade:] *= np.linspace(1, 0, loop_fade)
    
    save_mp3(normalize(signal, 0.35), "music-calm")

def gen_music_upbeat():
    """Upbeat rhythmic loop — energetic pulse (~30 seconds)."""
    duration = 30.0
    bpm = 120
    beat_duration = 60.0 / bpm
    t = np.linspace(0, duration, int(SAMPLE_RATE * duration), False)
    
    signal = np.zeros_like(t)
    
    # Chords: C -> G -> Am -> F (more energetic progression)
    chords = [
        (262, 330, 392),     # C: C4, E4, G4
        (196, 247, 294),     # G: G3, B3, D4
        (220, 262, 330),     # Am: A3, C4, E4
        (175, 220, 262),     # F: F3, A3, C4
    ]
    
    chord_len = duration / len(chords)
    
    for i, (f1, f2, f3) in enumerate(chords):
        start = int(i * chord_len * SAMPLE_RATE)
        end = int((i + 1) * chord_len * SAMPLE_RATE)
        seg_t = t[start:end] - t[start]
        seg_len = len(seg_t)
        
        # Brighter, more present tones
        pad = (np.sin(2 * np.pi * f1 * seg_t) * 0.2 +
               np.sin(2 * np.pi * f2 * seg_t) * 0.2 +
               np.sin(2 * np.pi * f3 * seg_t) * 0.15 +
               np.sin(2 * np.pi * (f1 * 2) * seg_t) * 0.08)  # Octave up
        
        # Rhythmic pulse (eighth notes)
        pulse_freq = 1.0 / (beat_duration / 2)
        pulse = 0.5 + 0.5 * np.sin(2 * np.pi * pulse_freq * seg_t - np.pi / 2)
        pulse = np.clip(pulse, 0.3, 1.0)
        
        envelope = np.ones(seg_len)
        crossfade = int(SAMPLE_RATE * 0.3)
        if crossfade < seg_len:
            envelope[:crossfade] = np.linspace(0, 1, crossfade)
            envelope[-crossfade:] = np.linspace(1, 0, crossfade)
        
        signal[start:end] += pad * pulse * envelope
    
    # Add rhythmic bass pulse
    bass_freq = 2.0 / beat_duration  # Hits on each beat
    bass_envelope = np.clip(0.5 + 0.5 * np.sin(2 * np.pi * bass_freq * t - np.pi / 2), 0, 1)
    bass = np.sin(2 * np.pi * 65 * t) * 0.15 * bass_envelope  # Low C
    signal += bass
    
    # Add higher energy shimmer
    shimmer = np.sin(2 * np.pi * 1047 * t) * 0.03 * (1 + np.sin(2 * np.pi * 0.5 * t)) * 0.5
    signal += shimmer
    
    # Loop fade
    loop_fade = int(SAMPLE_RATE * 1.5)
    signal[:loop_fade] *= np.linspace(0, 1, loop_fade)
    signal[-loop_fade:] *= np.linspace(1, 0, loop_fade)
    
    save_mp3(normalize(signal, 0.35), "music-upbeat")


if __name__ == "__main__":
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print("Generating sound effects...")
    gen_move()
    gen_capture()
    gen_check()
    gen_checkmate()
    gen_castle()
    gen_illegal()
    gen_undo()
    gen_game_start()
    gen_draw()
    print("\nGenerating background music...")
    gen_music_calm()
    gen_music_upbeat()
    print("\nDone! All files in:", OUTPUT_DIR)
