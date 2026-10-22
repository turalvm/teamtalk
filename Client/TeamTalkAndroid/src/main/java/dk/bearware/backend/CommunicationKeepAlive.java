package dk.bearware.backend;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Maintains an active USAGE_VOICE_COMMUNICATION AudioTrack stream with zero-amplitude (silence).
 * 
 * Why this is crucial:
 * Modern Android (especially Android 12, 13, 14 and Samsung One UI) enforces an orphaned communication
 * watchdog (CHECK_MODE_FOR_UID_PERIOD_MS = 6000). When TeamTalk plays incoming audio via native OpenSL ES,
 * Android's framework AudioService does not see an active Java voice communication track. After ~6 seconds
 * of idle or when TalkBack/screen reader speaks, Android's system audio policy drops MODE_IN_COMMUNICATION
 * back to MODE_NORMAL, dropping hardware Acoustic Echo Cancellation (AEC) and muting/ducking TeamTalk
 * as background media.
 * 
 * Keeping this lightweight silent AudioTrack playing ensures:
 * 1. The OS recognizes an ongoing active communication session on our UID.
 * 2. MODE_IN_COMMUNICATION is never stripped by the system watchdog.
 * 3. TalkBack ducking does not pause or drop the voice communication pipeline.
 * 4. Hardware AEC on the device's SoC stays permanently engaged while in speaker/earpiece mode.
 */
public class CommunicationKeepAlive {

    private static final String TAG = "bearware";

    private AudioTrack audioTrack;
    private Thread keepAliveThread;
    private volatile boolean isRunning = false;

    public synchronized void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;

        try {
            int sampleRate = 8000;
            int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            if (bufferSize <= 0) {
                bufferSize = 1024;
            }

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(audioFormat)
                    .setChannelMask(channelConfig)
                    .build();

            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            audioTrack.play();
            Log.d(TAG, "CommunicationKeepAlive AudioTrack started successfully (bufferSize=" + bufferSize + ")");

            final int finalBufferSize = bufferSize;
            keepAliveThread = new Thread(() -> {
                byte[] silentBuffer = new byte[finalBufferSize];
                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    try {
                        AudioTrack track = audioTrack;
                        if (track != null && isRunning && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                            track.write(silentBuffer, 0, silentBuffer.length, AudioTrack.WRITE_NON_BLOCKING);
                        }
                        Thread.sleep(80);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception ignored) {
                        break;
                    }
                }
            }, "TeamTalk-CommunicationKeepAlive");
            keepAliveThread.setDaemon(true);
            keepAliveThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start CommunicationKeepAlive: " + e.getMessage());
            isRunning = false;
        }
    }

    public synchronized void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;

        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
            keepAliveThread = null;
        }

        if (audioTrack != null) {
            try {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {}
            audioTrack = null;
        }
        Log.d(TAG, "CommunicationKeepAlive stopped");
    }

    public synchronized boolean isRunning() {
        return isRunning;
    }
}
