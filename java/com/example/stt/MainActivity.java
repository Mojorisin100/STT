package com.example.stt;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.ViewGroup;

import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;

import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private final String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private NavigationView navigationView;
    private LinearLayout activeSessionContainer;
    private LinearLayout startContainer;
    private ScrollView activeScrollView;
    private EditText transcriptTextView;
    private TextView sessionHeaderText;

    private ImageButton recordButton;

    private final StringBuilder accumulatedText = new StringBuilder();
    private boolean isRecording = false;
    private SpeechRecognitionManager speechManager;
    private GoogleApiManager googleApiManager;
    private String activeDocId = null;
    private String activeDocTitle = null;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 500;
    private final Handler handler = new Handler();

    // Field to hold the RedBallGame instance.
    private RedBallGame redBallGame = null;
    // Runnable to start the RedBallGame after 5 seconds of inactivity.
    private Runnable gameStarter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        // Get references to the two UI states.
        FrameLayout contentFrame = findViewById(R.id.contentFrame);
        startContainer = findViewById(R.id.startContainer);
        activeSessionContainer = findViewById(R.id.activeSessionContainer);
        activeScrollView = findViewById(R.id.activeScrollView);
        transcriptTextView = findViewById(R.id.transcriptTextView);
        sessionHeaderText = findViewById(R.id.sessionHeaderText);

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override public void onDrawerSlide(@NonNull View drawerView, float slideOffset) { }
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                View currentFocus = getCurrentFocus();
                if(currentFocus != null){
                    imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                }
                transcriptTextView.clearFocus();
                transcriptTextView.setCursorVisible(false);
            }
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                if(activeDocId != null){
                    transcriptTextView.setCursorVisible(true);
                }
            }
            @Override public void onDrawerStateChanged(int newState) { }
        });

        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setItemIconPadding(0);
        navigationView.setItemHorizontalPadding(0);
        navigationView.setItemVerticalPadding(0);
        navigationView.setPadding(0, 0, 0, 0);

        // Create Session listener.
        MenuItem createSessionItem = navigationView.getMenu().findItem(R.id.createSession);
        if(createSessionItem != null) {
            createSessionItem.setOnMenuItemClickListener(item -> {
                showCreateSessionDialog();
                return true;
            });
        }

        // Bottom toolbar buttons.
        ImageButton saveToClipboardButton = findViewById(R.id.saveToClipboardButton);
        ImageButton forwardButton = findViewById(R.id.forwardButton);
        recordButton = findViewById(R.id.recordButton);
        ImageButton refreshButton = findViewById(R.id.refreshButton);
        ImageButton deleteButton = findViewById(R.id.deleteButton);

        // Save to Clipboard action.
        saveToClipboardButton.setOnClickListener(v -> {
            if(activeDocId == null) {
                Toast.makeText(MainActivity.this, "Επιλέξτε ένα αρχείο...", Toast.LENGTH_SHORT).show();
                return;
            }
            String text = accumulatedText.toString();
            if(!text.isEmpty()){
                ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Κείμενο Αρχείου", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "Το αρχείο αντιγράφηκε στο πρόχειρο", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Δεβ υπάρχει κείμενο για αντιγραφή", Toast.LENGTH_SHORT).show();
            }
        });

        // Forward Email action.
        forwardButton.setOnClickListener(v -> {
            if(activeDocId == null) {
                Toast.makeText(MainActivity.this, "Επιλέξτε ένα αρχείο...", Toast.LENGTH_SHORT).show();
                return;
            }
            String subject = activeDocTitle;
            String body = accumulatedText.toString();
            EmailSender.sendEmail(subject, body, new EmailSender.EmailCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Το email εστάλη επιτυχώς", Toast.LENGTH_SHORT).show()
                    );
                }
                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Σφάλμα στην αποστολή emailL: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            });
        });

        // Delete session action.
        deleteButton.setOnClickListener(v -> {
            if(activeDocId == null){
                Toast.makeText(MainActivity.this, "Επιλέξτε ένα αρχείο...", Toast.LENGTH_SHORT).show();
                return;
            }
            showDeleteConfirmation(activeDocTitle, () -> {
                new Thread(() -> {
                    googleApiManager.deleteDocument(activeDocId);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Επιτυχής διαγραφή του " + activeDocTitle, Toast.LENGTH_SHORT).show();
                        handlePostDeletion(activeDocId);
                    });
                }).start();
            });
        });

        // Record and Refresh actions.
        recordButton.setOnClickListener(v -> toggleRecording());
        refreshButton.setOnClickListener(v -> {
            if(activeDocId == null){
                Toast.makeText(MainActivity.this, "Επιλέξτε ένα αρχείο...", Toast.LENGTH_SHORT).show();
                return;
            }
            String newContent = transcriptTextView.getText().toString();
            final String safeContent = newContent.trim().isEmpty() ? " " : newContent;
            new Thread(() -> {
                if(activeDocId != null){
                    googleApiManager.setDocumentContent(activeDocId, safeContent);
                }
            }).start();
            Toast.makeText(MainActivity.this, "Το αρχείο ενημερώθηκε", Toast.LENGTH_SHORT).show();
        });

        googleApiManager = new GoogleApiManager(this);
        speechManager = new SpeechRecognitionManager(this, transcript -> {
            if(activeDocId == null){
                Toast.makeText(MainActivity.this, "Επιλέξτε ένα αρχείο...", Toast.LENGTH_SHORT).show();
                return;
            }
            String processed = Utils.processPunctuation(transcript);
            String formatted = Utils.formatText(processed);
            long currentTime = System.currentTimeMillis();
            if(currentTime - lastUpdateTime < UPDATE_INTERVAL_MS) return;
            lastUpdateTime = currentTime;

            runOnUiThread(() -> {
                int cursorPos = transcriptTextView.getSelectionStart();
                String currentText = transcriptTextView.getText().toString();
                String insertText = (cursorPos > 0 && !Character.isWhitespace(currentText.charAt(cursorPos - 1)))
                        ? " " + formatted : formatted;
                String newText = currentText.substring(0, cursorPos) + insertText + currentText.substring(cursorPos);
                transcriptTextView.setText(newText);
                transcriptTextView.setSelection(cursorPos + insertText.length());
                accumulatedText.setLength(0);
                accumulatedText.append(newText);
                activeScrollView.post(() -> activeScrollView.fullScroll(View.FOCUS_DOWN));
                final String safeText = newText.trim().isEmpty() ? " " : newText;
                new Thread(() -> googleApiManager.setDocumentContent(activeDocId, safeText)).start();
            });
        });

        refreshSessionList();

        // --- Integrate RedBallGame into the no-session (start) screen ---
        gameStarter = new Runnable() {
            @Override
            public void run() {
                if (activeDocId == null && startContainer.getVisibility() == View.VISIBLE) {
                    DisplayMetrics dm = getResources().getDisplayMetrics();
                    int width = dm.widthPixels;
                    int height = dm.heightPixels;
                    // Use a FrameLayout as container so that views can overlap.
                    FrameLayout container = new FrameLayout(MainActivity.this);
                    container.setLayoutParams(new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    // Create the game view.
                    redBallGame = new RedBallGame(MainActivity.this, width, height);
                    container.addView(redBallGame);
                    redBallGame.resume();
                    // Create the transitional layout (which mimics the XML starting screen).
                    LinearLayout transitionLayout = new LinearLayout(MainActivity.this);
                    transitionLayout.setOrientation(LinearLayout.VERTICAL);
                    FrameLayout.LayoutParams transitionParams = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    transitionLayout.setLayoutParams(transitionParams);
                    transitionLayout.setGravity(Gravity.CENTER);
                    int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
                    transitionLayout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
                    ImageView transitionImage = new ImageView(MainActivity.this);
                    LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    imageParams.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
                    transitionImage.setLayoutParams(imageParams);
                    transitionImage.setImageResource(R.drawable.speech_to_text4);
                    transitionImage.setContentDescription("Microphone Icon");
                    transitionImage.setAdjustViewBounds(true);
                    transitionImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    TextView promptText = new TextView(MainActivity.this);
                    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    promptText.setLayoutParams(textParams);
                    promptText.setText("Επιλέξτε ένα αρχείο...");
                    promptText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.textColor));
                    promptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
                    promptText.setGravity(Gravity.CENTER);
                    transitionLayout.addView(transitionImage);
                    transitionLayout.addView(promptText);
                    // Add the transitional overlay on top of the game view.
                    container.addView(transitionLayout);
                    // Set the container as the sole content of startContainer.
                    startContainer.removeAllViews();
                    startContainer.addView(container);
                    // After 1 second, remove the transitional overlay.
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            container.removeView(transitionLayout);
                        }
                    }, 1400);
                }
            }
        };
        handler.postDelayed(gameStarter, 30000);
    }

    // Override onUserInteraction to reset the inactivity timer only when the game hasn't started.
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (activeDocId == null && redBallGame == null) {
            handler.removeCallbacks(gameStarter);
            handler.postDelayed(gameStarter, 30000);
        }
    }

    // Cancel the RedBallGame if a session is started.
    private void cancelRedBallGameIfActive() {
        handler.removeCallbacks(gameStarter);
        if (redBallGame != null) {
            redBallGame.pause();
            startContainer.removeAllViews();
            redBallGame = null;
        }
    }

    private void showCreateSessionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Τίτλος Αρχείου");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String sessionTitle = input.getText().toString().trim();
            if (!sessionTitle.isEmpty()) {
                new Thread(() -> {
                    String docId = googleApiManager.createDocument(sessionTitle, "");
                    runOnUiThread(() -> {
                        if (docId != null) {
                            cancelRedBallGameIfActive();
                            activeDocId = docId;
                            activeDocTitle = sessionTitle;
                            String content = googleApiManager.getDocumentContent(docId);
                            transcriptTextView.setText(content);
                            transcriptTextView.setEnabled(true);
                            transcriptTextView.setBackgroundColor(Color.WHITE);
                            transcriptTextView.setGravity(Gravity.TOP | Gravity.START);
                            transcriptTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                            transcriptTextView.setSelection(transcriptTextView.getText().length());
                            sessionHeaderText.setText(sessionTitle);
                            startContainer.setVisibility(View.GONE);
                            activeSessionContainer.setVisibility(View.VISIBLE);

                            Toast.makeText(MainActivity.this, "Επιτυχής φόρτωση " + sessionTitle, Toast.LENGTH_SHORT).show();
                            accumulatedText.setLength(0);
                            accumulatedText.append(content);
                            if (!content.endsWith(" ") && !content.endsWith("\n")) {
                                accumulatedText.append(" ");
                                new Thread(() -> {
                                    String safeContent = accumulatedText.toString().trim().isEmpty() ? " " : accumulatedText.toString();
                                    googleApiManager.setDocumentContent(activeDocId, safeContent);
                                }).start();
                            }
                            refreshSessionList();
                        } else {
                            Toast.makeText(MainActivity.this, "Σφάλμα στη δημιουργία αρχείου", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void toggleRecording() {
        if (activeDocId == null) {
            Toast.makeText(this, "Επιλέξτε ένα αρχείο...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isRecording) {
            isRecording = true;
            recordButton.setColorFilter(Color.RED);
            speechManager.startListening();
        } else {
            isRecording = false;
            recordButton.setColorFilter(ContextCompat.getColor(this, R.color.iconTint));
            speechManager.stopListening();
        }
    }

    private void showDeleteConfirmation(String sessionName, Runnable onDelete) {
        new AlertDialog.Builder(this)
                .setTitle("Επιβεβαίωση Διαγραφής")
                .setMessage("Είστε σίγουροι ότι θέλετε να διαγράψετε το " + sessionName + "?")
                .setPositiveButton("Διαγραφή", (dialog, which) -> onDelete.run())
                .setNegativeButton("Άκυρο", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void handlePostDeletion(String deletedSessionId) {
        new Thread(() -> {
            final List<GoogleApiManager.Pair> sessions = googleApiManager.listDocuments();
            runOnUiThread(() -> {
                if (activeDocId != null && activeDocId.equals(deletedSessionId)) {
                    if (sessions == null || sessions.isEmpty()) {
                        forceRestartApp();
                        return;
                    } else {
                        GoogleApiManager.Pair nextSession = sessions.get(0);
                        activeDocId = nextSession.id;
                        activeDocTitle = nextSession.name;
                        String content = googleApiManager.getDocumentContent(nextSession.id);
                        transcriptTextView.setText(content);
                        transcriptTextView.setEnabled(true);
                        transcriptTextView.setBackgroundColor(Color.WHITE);
                        transcriptTextView.setGravity(Gravity.TOP | Gravity.START);
                        transcriptTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        transcriptTextView.setSelection(transcriptTextView.getText().length());
                        sessionHeaderText.setText(nextSession.name);
                        startContainer.setVisibility(View.GONE);
                        activeSessionContainer.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, "Επιτυχής φόρτωση " + nextSession.name, Toast.LENGTH_SHORT).show();
                        accumulatedText.setLength(0);
                        accumulatedText.append(content);
                        if (!content.endsWith(" ") && !content.endsWith("\n")) {
                            accumulatedText.append(" ");
                            new Thread(() -> {
                                String safeContent = accumulatedText.toString().trim().isEmpty() ? " " : accumulatedText.toString();
                                googleApiManager.setDocumentContent(activeDocId, safeContent);
                            }).start();
                        }
                    }
                }
                refreshSessionList();
            });
        }).start();
    }

    private void forceRestartApp() {
        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        assert intent != null;
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void refreshSessionList() {
        new Thread(() -> {
            final List<GoogleApiManager.Pair> sessions = googleApiManager.listDocuments();
            runOnUiThread(() -> {
                Menu menu = navigationView.getMenu();
                menu.removeGroup(R.id.sessionsGroup);
                if (sessions != null && !sessions.isEmpty()) {
                    for (int index = 0; index < sessions.size(); index++) {
                        final GoogleApiManager.Pair session = sessions.get(index);
                        final MenuItem item = menu.add(R.id.sessionsGroup, Menu.NONE, Menu.NONE, "");
                        item.setActionView(R.layout.session_item);
                        final android.widget.LinearLayout container = Objects.requireNonNull(item.getActionView()).findViewById(R.id.sessionContainer);
                        if (index == 0) {
                            android.widget.LinearLayout.LayoutParams params = (android.widget.LinearLayout.LayoutParams) container.getLayoutParams();
                            params.topMargin = 0;
                            container.setLayoutParams(params);
                        }
                        final CheckBox sessionCheckbox = item.getActionView().findViewById(R.id.sessionCheckbox);
                        final android.widget.TextView sessionNameView = item.getActionView().findViewById(R.id.sessionName);
                        sessionNameView.setText(session.name);
                        if(activeDocId != null && activeDocId.equals(session.id)) {
                            sessionCheckbox.setChecked(true);
                            container.setBackgroundResource(R.drawable.session_selected);
                            sessionCheckbox.setButtonTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.red));
                        } else {
                            sessionCheckbox.setChecked(false);
                            container.setBackgroundResource(android.R.color.transparent);
                            sessionCheckbox.setButtonTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.iconTint));
                        }
                        sessionCheckbox.setOnClickListener(v -> {
                            if(activeDocId != null && activeDocId.equals(session.id)) {
                                sessionCheckbox.setChecked(true);
                                return;
                            }
                            for (int i = 0; i < menu.size(); i++) {
                                MenuItem otherItem = menu.getItem(i);
                                if(otherItem.getGroupId() == R.id.sessionsGroup) {
                                    CheckBox otherCheckbox = Objects.requireNonNull(otherItem.getActionView()).findViewById(R.id.sessionCheckbox);
                                    android.widget.LinearLayout otherContainer = otherItem.getActionView().findViewById(R.id.sessionContainer);
                                    if(otherCheckbox != null) {
                                        otherCheckbox.setChecked(false);
                                        otherContainer.setBackgroundResource(android.R.color.transparent);
                                        otherCheckbox.setButtonTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.iconTint));
                                    }
                                }
                            }
                            sessionCheckbox.setChecked(true);
                            container.setBackgroundResource(R.drawable.session_selected);
                            sessionCheckbox.setButtonTintList(ContextCompat.getColorStateList(MainActivity.this, R.color.red));
                            new Thread(() -> {
                                final String content = googleApiManager.getDocumentContent(session.id);
                                runOnUiThread(() -> {
                                    cancelRedBallGameIfActive();
                                    activeDocId = session.id;
                                    activeDocTitle = session.name;
                                    transcriptTextView.setText(content);
                                    transcriptTextView.setEnabled(true);
                                    transcriptTextView.setBackgroundColor(Color.WHITE);
                                    transcriptTextView.setGravity(Gravity.TOP | Gravity.START);
                                    transcriptTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                                    transcriptTextView.setSelection(transcriptTextView.getText().length());
                                    sessionHeaderText.setText(session.name);
                                    startContainer.setVisibility(View.GONE);
                                    activeSessionContainer.setVisibility(View.VISIBLE);
                                    drawerLayout.closeDrawers();
                                    Toast.makeText(MainActivity.this, "Επιτυχής φόρτωση " + session.name, Toast.LENGTH_SHORT).show();
                                    activeScrollView.post(() -> activeScrollView.fullScroll(View.FOCUS_DOWN));
                                    accumulatedText.setLength(0);
                                    accumulatedText.append(content);
                                    if (!content.endsWith(" ") && !content.endsWith("\n")) {
                                        accumulatedText.append(" ");
                                        new Thread(() -> {
                                            String safeContent = accumulatedText.toString().trim().isEmpty() ? " " : accumulatedText.toString();
                                            googleApiManager.setDocumentContent(activeDocId, safeContent);
                                        }).start();
                                    }
                                });
                            }).start();
                        });

                        android.widget.ImageButton forwardButton = item.getActionView().findViewById(R.id.forwardButton);
                        forwardButton.setOnClickListener(v -> {
                            new Thread(() -> {
                                final String content = googleApiManager.getDocumentContent(session.id);
                                runOnUiThread(() -> {
                                    cancelRedBallGameIfActive();
                                    activeDocId = session.id;
                                    activeDocTitle = session.name;
                                    transcriptTextView.setText(content);
                                    transcriptTextView.setEnabled(true);
                                    transcriptTextView.setBackgroundColor(Color.WHITE);
                                    transcriptTextView.setGravity(Gravity.TOP | Gravity.START);
                                    transcriptTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                                    transcriptTextView.setSelection(transcriptTextView.getText().length());
                                    accumulatedText.setLength(0);
                                    accumulatedText.append(content);
                                    if (!content.endsWith(" ") && !content.endsWith("\n")) {
                                        accumulatedText.append(" ");
                                        new Thread(() -> {
                                            String safeContent = accumulatedText.toString().trim().isEmpty() ? " " : accumulatedText.toString();
                                            googleApiManager.setDocumentContent(activeDocId, safeContent);
                                        }).start();
                                    }
                                    String subject = activeDocTitle;
                                    String body = accumulatedText.toString();
                                    EmailSender.sendEmail(subject, body, new EmailSender.EmailCallback() {
                                        @Override
                                        public void onSuccess() {
                                            runOnUiThread(() ->
                                                    Toast.makeText(MainActivity.this, "Το email εστάλη επιτυχώς", Toast.LENGTH_SHORT).show()
                                            );
                                        }
                                        @Override
                                        public void onFailure(Exception e) {
                                            runOnUiThread(() ->
                                                    Toast.makeText(MainActivity.this, "Σφάλμα στην αποστολή email: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                            );
                                        }
                                    });
                                });
                            }).start();
                        });

                        android.widget.ImageButton deleteButton = item.getActionView().findViewById(R.id.deleteButton);
                        deleteButton.setOnClickListener(v -> {
                            showDeleteConfirmation(session.name, () -> {
                                new Thread(() -> {
                                    googleApiManager.deleteDocument(session.id);
                                    runOnUiThread(() -> {
                                        Toast.makeText(MainActivity.this, "Επιτυχής διαγραφή " + session.name, Toast.LENGTH_SHORT).show();
                                        if(activeDocId != null && activeDocId.equals(session.id)) {
                                            handlePostDeletion(session.id);
                                        } else {
                                            refreshSessionList();
                                        }
                                    });
                                }).start();
                            });
                        });

                        android.widget.ImageButton saveToClipboardButton = item.getActionView().findViewById(R.id.saveToClipboardButton);
                        saveToClipboardButton.setOnClickListener(v -> {
                            String text = accumulatedText.toString();
                            if(!text.isEmpty()){
                                ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("Session Text", text);
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(MainActivity.this, "Αντιγράφηκε στο πρόχειρο", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Δεν υπάρχει κείμενο για αντιγραφή", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    menu.add(R.id.sessionsGroup, Menu.NONE, Menu.NONE, "Δεν υπάρχουν αρχεία");
                }
            });
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return toggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.createSession) {
            showCreateSessionDialog();
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_RECORD_AUDIO_PERMISSION){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                // Permission granted.
            } else {
                Toast.makeText(this, "Η άδεια του μικροφώνου απέτυχε", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(speechManager != null){
            speechManager.destroy();
        }
    }
}
