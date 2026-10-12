package com.plaguevoice;

/**
 * Real time "Dottore della Peste" (plague doctor) voice processor.
 *
 * Pure Java DSP, no external dependencies, so the project builds cleanly anywhere.
 *
 * Signal chain (per sample, 44.1 kHz mono):
 *   1. Pitch shift down  - a two-tap, Hann-crossfaded modulated delay line.
 *   2. Chest resonance   - peaking biquad around 230 Hz (adds weight / body).
 *   3. Mask muffle       - two cascaded one-pole low passes (voice behind a mask).
 *   4. Respirator buzz   - gentle ring modulation (~46 Hz) for the breathing mask.
 *   5. Gravel saturation - asymmetric soft clip (tanh) for a raspy growl.
 *   6. Cave reverb       - Schroeder reverb (4 combs + 2 allpass).
 *   7. DC blocker, gain and limiting.
 */
public class PlagueVoiceProcessor {

    public static final int FS = 44100;

    private static final int MASK_BITS = 13;
    private static final int LINE_SIZE = 1 << MASK_BITS;   // 8192 samples
    private static final int MASK = LINE_SIZE - 1;
    private static final int WIN = 2048;                   // ~46 ms pitch window

    private final float[] line = new float[LINE_SIZE];
    private int writeIdx = 0;
    private double phase = 0.0;
    private double phaseInc = 0.0;
    private final float twoPi = (float) (2.0 * Math.PI);

    // 3) muffle low pass
    private float lp1 = 0f, lp2 = 0f;
    private float lpCoef = 0.34f;

    // 2) chest peaking biquad
    private float pb0, pb1, pb2, pa1, pa2;
    private float px1, px2, py1, py2;

    // 4) respirator ring modulation
    private double ringPhase = 0.0;
    private double ringInc;
    private float ringDepth = 0.12f;

    // 6) Schroeder reverb
    private final float[] comb1 = new float[1291];
    private final float[] comb2 = new float[1607];
    private final float[] comb3 = new float[1801];
    private final float[] comb4 = new float[1913];
    private final float[] ap1 = new float[556];
    private final float[] ap2 = new float[441];
    private int c1 = 0, c2 = 0, c3 = 0, c4 = 0, a1 = 0, a2 = 0;
    private float combFb = 0.78f;
    private float wetMix = 0.20f;

    // 7) DC blocker
    private float dcX = 0f, dcY = 0f;

    private float pitchSemitones = -7f;
    private float growl = 0.55f;
    private float outGain = 0.92f;

    public PlagueVoiceProcessor() {
        configureChest();
        setPitchSemitones(pitchSemitones);
        setReverb(0.35f);
        ringInc = 2.0 * Math.PI * 46.0 / FS;
    }

    public void setPitchSemitones(float semitones) {
        if (semitones > 0f) semitones = 0f;
        if (semitones < -18f) semitones = -18f;
        pitchSemitones = semitones;
        double ratio = Math.pow(2.0, semitones / 12.0);
        phaseInc = (1.0 - ratio) / WIN;
    }

    public void setGrowl(float g) {
        growl = clamp(g, 0f, 1f);
    }

    public void setReverb(float amount) {
        amount = clamp(amount, 0f, 1f);
        wetMix = 0.10f + amount * 0.40f;
        combFb = 0.70f + amount * 0.18f;
    }

    public void setOutputGain(float g) {
        outGain = clamp(g, 0f, 1.5f);
    }

    private void configureChest() {
        double f0 = 230.0, q = 1.1, dbGain = 7.0;
        double a = Math.pow(10.0, dbGain / 40.0);
        double w0 = 2.0 * Math.PI * f0 / FS;
        double cosw = Math.cos(w0);
        double alpha = Math.sin(w0) / (2.0 * q);
        double a0 = 1.0 + alpha / a;
        pb0 = (float) ((1.0 + alpha * a) / a0);
        pb1 = (float) ((-2.0 * cosw) / a0);
        pb2 = (float) ((1.0 - alpha * a) / a0);
        pa1 = (float) ((-2.0 * cosw) / a0);
        pa2 = (float) ((1.0 - alpha / a) / a0);
    }

    public void reset() {
        java.util.Arrays.fill(line, 0f);
        java.util.Arrays.fill(comb1, 0f);
        java.util.Arrays.fill(comb2, 0f);
        java.util.Arrays.fill(comb3, 0f);
        java.util.Arrays.fill(comb4, 0f);
        java.util.Arrays.fill(ap1, 0f);
        java.util.Arrays.fill(ap2, 0f);
        writeIdx = 0;
        phase = 0.0;
        c1 = c2 = c3 = c4 = a1 = a2 = 0;
        lp1 = lp2 = 0f;
        px1 = px2 = py1 = py2 = 0f;
        dcX = dcY = 0f;
    }

    /** Processes n mono samples from in into out (in place safe if in == out). */
    public void process(float[] in, float[] out, int n) {
        for (int i = 0; i < n; i++) {
            float x = in[i];

            // 1) Pitch shift down.
            line[writeIdx & MASK] = x;
            double p0 = phase;
            double p1 = phase + 0.5;
            if (p1 >= 1.0) p1 -= 1.0;

            float s0 = readFrac(line, writeIdx - WIN * p0);
            float s1 = readFrac(line, writeIdx - WIN * p1);
            float g0 = 0.5f * (1f - (float) Math.cos(twoPi * p0));
            float g1 = 0.5f * (1f - (float) Math.cos(twoPi * p1));
            float y = s0 * g0 + s1 * g1;

            writeIdx++;
            phase += phaseInc;
            if (phase >= 1.0) {
                phase -= 1.0;
            } else if (phase < 0.0) {
                phase += 1.0;
            }

            // 2) Chest resonance.
            float xin = y;
            float yout = pb0 * xin + pb1 * px1 + pb2 * px2 - pa1 * py1 - pa2 * py2;
            px2 = px1;
            px1 = xin;
            py2 = py1;
            py1 = yout;
            y = yout;

            // 3) Mask muffle.
            lp1 += lpCoef * (y - lp1);
            lp2 += lpCoef * (lp1 - lp2);
            y = lp2;

            // 4) Respirator ring modulation.
            float carrier = (float) Math.sin(ringPhase);
            ringPhase += ringInc;
            if (ringPhase >= 2.0 * Math.PI) {
                ringPhase -= 2.0 * Math.PI;
            }
            y *= (1f - ringDepth) + ringDepth * carrier;

            // 5) Gravel saturation (asymmetric soft clip).
            float drive = 1f + growl * 5f;
            float bias = growl * 0.12f;
            y = (float) (Math.tanh(drive * y + bias) / Math.tanh(drive));

            // 6) Cave reverb.
            float rv = reverb(y);
            y = y * (1f - wetMix) + rv * wetMix;

            // 7) DC blocker, gain and limit.
            float dc = y - dcX + 0.995f * dcY;
            dcX = y;
            dcY = dc;
            float o = dc * outGain;
            if (o > 1f) {
                o = 1f;
            } else if (o < -1f) {
                o = -1f;
            }
            out[i] = o;
        }
    }

    private static float readFrac(float[] b, double pos) {
        long i0 = (long) Math.floor(pos);
        float frac = (float) (pos - i0);
        float va = b[(int) i0 & MASK];
        float vb = b[(int) (i0 + 1) & MASK];
        return va + (vb - va) * frac;
    }

    private float reverb(float input) {
        float acc = 0f;
        float c;

        c = comb1[c1];
        comb1[c1] = input + c * combFb;
        c1 = (c1 + 1) % comb1.length;
        acc += c;

        c = comb2[c2];
        comb2[c2] = input + c * combFb;
        c2 = (c2 + 1) % comb2.length;
        acc += c;

        c = comb3[c3];
        comb3[c3] = input + c * combFb;
        c3 = (c3 + 1) % comb3.length;
        acc += c;

        c = comb4[c4];
        comb4[c4] = input + c * combFb;
        c4 = (c4 + 1) % comb4.length;
        acc += c;

        acc *= 0.25f;

        float buf = ap1[a1];
        float o1 = -acc + buf;
        ap1[a1] = acc + buf * 0.5f;
        a1 = (a1 + 1) % ap1.length;

        float buf2 = ap2[a2];
        float o2 = -o1 + buf2;
        ap2[a2] = o1 + buf2 * 0.5f;
        a2 = (a2 + 1) % ap2.length;

        return o2;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
