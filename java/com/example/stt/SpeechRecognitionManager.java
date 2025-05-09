package com.example.stt;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import java.util.ArrayList;

public class SpeechRecognitionManager {

    public interface TranscriptListener {
        void onTranscriptUpdate(String transcript);
    }

    private final Context context;
    private final TranscriptListener listener;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;

    public SpeechRecognitionManager(Context context, TranscriptListener listener) {
        this.context = context;
        this.listener = listener;
        initialize();
    }

    private void initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("SpeechRecognition", "Speech recognition is not available on this device.");
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d("SpeechRecognition", "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d("SpeechRecognition", "Speech has begun");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Optionally update UI with the input volume.
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // Not used.
            }

            @Override
            public void onEndOfSpeech() {
                Log.d("SpeechRecognition", "Speech ended");
            }

            @Override
            public void onError(int error) {
                Log.e("SpeechRecognition", "Error: " + error);
                if (isListening) {
                    restartListening();
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    // Removed trim() to preserve exact spacing.
                    String transcript = matches.get(0);
                    if (transcript != null && !transcript.isEmpty()) {
                        listener.onTranscriptUpdate(transcript);
                    }
                }
                if (isListening) {
                    restartListening();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // Ignore partial results.
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // Not used.
            }
        });
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "el-GR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
    }

    public void startListening() {
        if (speechRecognizer != null && !isListening) {
            isListening = true;
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            isListening = false;
            speechRecognizer.stopListening();
        }
    }

    private void restartListening() {
        if (isListening) {
            speechRecognizer.cancel();
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }
}
