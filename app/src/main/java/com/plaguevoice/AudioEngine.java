package com.plaguevoice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;
import android.util.Log;

/**
 * Captures the microphone, runs the plague-doctor processor and plays the result.
 */
public class AudioEngine {

    private static final String TAG = "PlagueVoiceEngine";
    private static final int FRAME = 256;

    private final Context context;
    private final PlagueVoiceProcessor processor = new PlagueVoiceProcessor();

    private AudioRecord record;
    private AudioTrack track;
    private Thread thread;
    private volatile boolean running = false;
    private boolean scoStarted = false;
    private boolean communicationDeviceSet = false;

    public AudioEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRunning() {
        return running;
    }

    public void configure(float pitchSemitones, float growl, float reverb) {
        processor.setPitchSemitones(pitchSemitones);
        processor.setGrowl(growl);
        processor.setReverb(reverb);
    }

    public void start(int inputDeviceId, int outputDeviceId) {
        if (running) {
            return;
        }
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo inDev = (inputDeviceId >= 0) ? DeviceUtils.find(context, inputDeviceId, false) : null;
            AudioDeviceInfo outDev = (outputDeviceId >= 0) ? DeviceUtils.find(context, outputDeviceId, true) : null;

            boolean isBluetoothInput = false;
            if (inDev != null) {
                int type = inDev.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == 26 /* TYPE_BLE_HEADSET */) {
                    isBluetoothInput = true;
                }
            }

            if (isBluetoothInput && am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    boolean success = am.setCommunicationDevice(inDev);
                    if (success) {
                        communicationDeviceSet = true;
                    } else {
                        Log.w(TAG, "setCommunicationDevice returned false for " + inDev.getProductName());
                    }
                } else {
                    am.startBluetoothSco();
                    am.setBluetoothScoOn(true);
                    scoStarted = true;
                }
            }

            int audioSource = isBluetoothInput
                    ? MediaRecorder.AudioSource.VOICE_COMMUNICATION
                    : MediaRecorder.AudioSource.MIC;

            int minIn = AudioRecord.getMinBufferSize(
                    PlagueVoiceProcessor.FS,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int inBuf = Math.max(minIn, FRAME * 2 * 6);

            AudioFormat inFormat = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(PlagueVoiceProcessor.FS)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            record = new AudioRecord.Builder()
                    .setAudioSource(audioSource)
                    .setAudioFormat(inFormat)
                    .setBufferSizeInBytes(inBuf)
                    .build();

            int minOut = AudioTrack.getMinBufferSize(
                    PlagueVoiceProcessor.FS,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int outBuf = Math.max(minOut, FRAME * 2 * 6);

            AudioFormat outFormat = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(PlagueVoiceProcessor.FS)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(outFormat)
                    .setBufferSizeInBytes(outBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized");
                release();
                return;
            }
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack not initialized");
                release();
                return;
            }

            if (outDev != null) {
                track.setPreferredDevice(outDev);
            }
            if (inDev != null) {
                record.setPreferredDevice(inDev);
            }

            processor.reset();
            running = true;
            thread = new Thread(this::loop, "PlagueVoiceAudio");
            thread.start();
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
            running = false;
            release();
        }
    }

    private void loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        short[] inS = new short[FRAME];
        short[] outS = new short[FRAME];
        float[] inF = new float[FRAME];
        float[] outF = new float[FRAME];
        try {
            record.startRecording();
            track.play();
            while (running) {
                int n = record.read(inS, 0, FRAME);
                if (n <= 0) {
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    inF[i] = inS[i] / 32768f;
                }
                processor.process(inF, outF, n);
                for (int i = 0; i < n; i++) {
                    float v = outF[i];
                    if (v > 1f) {
                        v = 1f;
                    } else if (v < -1f) {
                        v = -1f;
                    }
                    outS[i] = (short) (v * 32767f);
                }
                track.write(outS, 0, n);
            }
        } catch (Exception e) {
            Log.e(TAG, "loop error", e);
        } finally {
            try {
                if (record != null) {
                    record.stop();
                }
            } catch (Exception ignored) {
            }
            try {
                if (track != null) {
                    track.stop();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void stop() {
        running = false;
        Thread t = thread;
        if (t != null) {
            try {
                t.join(600);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        release();
    }

    private void release() {
        running = false;
        try {
            if (record != null) {
                record.release();
            }
        } catch (Exception ignored) {
        }
        try {
            if (track != null) {
                track.release();
            }
        } catch (Exception ignored) {
        }
        record = null;
        track = null;
        thread = null;

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            if (communicationDeviceSet) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice();
                }
                communicationDeviceSet = false;
            }
            if (scoStarted) {
                am.stopBluetoothSco();
                am.setBluetoothScoOn(false);
                scoStarted = false;
            }
        }
    }
}
