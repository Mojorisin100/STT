package com.example.stt;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class RedBallGame extends SurfaceView implements Runnable {

    private Thread gameThread;
    private boolean playing;
    private final SurfaceHolder surfaceHolder;
    private final Paint paint;

    private final int screenWidth;
    private final int screenHeight;
    private final int gameAreaHeight; // effective game area height (screen height minus bottom toolbar area)

    // Ball properties.
    private float ballX, ballY;
    private float ballRadius;
    private float ballSpeedX, ballSpeedY;

    // Paddle (board) properties.
    private RectF paddle;
    private float paddleWidth;

    // Score counter.
    private int score;

    // Tracking the last threshold for speed increase.
    private int lastThreshold;

    // Game over flag.
    private boolean gameOver;

    // Flag to update best score only once.
    private boolean bestScoreUpdated;

    // Rectangle representing the drawn "Play Again" button.
    private RectF playAgainButton;

    // Added vertical tolerance for touch events on the paddle.
    private static final float TOUCH_VERTICAL_TOLERANCE = 500.0f;

    // SharedPreferences keys.
    private static final String PREFS_NAME = "RedBallGamePrefs";
    private static final String KEY_BEST_SCORE = "best_score";

    public RedBallGame(Context context, int screenWidth, int screenHeight) {
        super(context);
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        // Adjust this value to match your bottom toolbar's height.
        int bottomMargin = 300;
        // The game area is the screen height minus the bottom margin.
        gameAreaHeight = screenHeight - bottomMargin;
        surfaceHolder = getHolder();
        paint = new Paint();
        initGame();
    }

    private void initGame() {
        // Initialize ball at a custom position.
        ballRadius = screenWidth / 50.0f;
        ballX = screenWidth / 1.5f;
        ballY = gameAreaHeight / 2.65f;

        // Base speeds.
        float baseSpeedX = screenWidth / 150.0f;
        float baseSpeedY = screenHeight / 150.0f;
        float multiplier = 1.3f;

        ballSpeedX = multiplier * baseSpeedX;
        ballSpeedY = -multiplier * baseSpeedY;  // Negative for upward movement.

        // Initialize a thicker paddle.
        paddleWidth = screenWidth / 5.0f;
        // Increase paddle height to make it fatter.
        float paddleHeight = gameAreaHeight / 30.0f;
        float paddleLeft = (screenWidth - paddleWidth) / 2.0f;
        float paddleTop = gameAreaHeight * 0.92f; // Places the paddle 25% of the game area height down from the top.
        paddle = new RectF(paddleLeft, paddleTop, paddleLeft + paddleWidth, paddleTop + paddleHeight);

        // Reset score and game state.
        score = 0;
        lastThreshold = 0;
        gameOver = false;
        bestScoreUpdated = false;
        playAgainButton = null;
    }


    public void resume() {
        playing = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    public void pause() {
        playing = false;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            Log.e("MyApp", "An error occurred", e);
        }
    }

    @Override
    public void run() {
        while (playing) {
            update();
            draw();
            control();
        }
    }

    private void update() {
        if (gameOver) return;

        // Update ball position.
        ballX += ballSpeedX;
        ballY += ballSpeedY;

        // Bounce off left wall.
        if (ballX - ballRadius < 0) {
            ballX = ballRadius;
            ballSpeedX = -ballSpeedX;
        }
        // Bounce off right wall.
        if (ballX + ballRadius > screenWidth) {
            ballX = screenWidth - ballRadius;
            ballSpeedX = -ballSpeedX;
        }
        // Bounce off top wall.
        if (ballY - ballRadius < 0) {
            ballY = ballRadius;
            ballSpeedY = -ballSpeedY;
        }

        // Check collision with paddle if the ball is falling.
        if (ballSpeedY > 0 && ballY + ballRadius >= paddle.top && ballY + ballRadius <= paddle.bottom) {
            if (ballX >= paddle.left && ballX <= paddle.right) {
                ballY = paddle.top - ballRadius;
                ballSpeedY = -ballSpeedY;
                score++;

                // Increase speed every 5 bounces.
                if (score >= lastThreshold + 5) {
                    ballSpeedX *= 1.3f;
                    ballSpeedY *= 1.3f;
                    lastThreshold += 5;
                }
            }
        }

        // If ball falls below the effective game area, mark game over and update best score.
        if (ballY - ballRadius > gameAreaHeight) {
            gameOver = true;
            if (!bestScoreUpdated) {
                SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                int bestScore = prefs.getInt(KEY_BEST_SCORE, 0);
                if (score > bestScore) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt(KEY_BEST_SCORE, score);
                    editor.apply();
                }
                bestScoreUpdated = true;
            }
        }
    }

    private void draw() {
        if (surfaceHolder.getSurface().isValid()) {
            Canvas canvas = surfaceHolder.lockCanvas();

            // Draw white background.
            canvas.drawColor(Color.WHITE);

            // Draw the paddle with rounded edges.
            paint.setColor(Color.BLACK);
            float cornerRadius = 20.0f; // Adjust this value for more or less rounded corners.
            canvas.drawRoundRect(paddle, cornerRadius, cornerRadius, paint);

            // Draw the ball.
            paint.setColor(Color.RED);
            canvas.drawCircle(ballX, ballY, ballRadius, paint);

            // Draw the current score.
            paint.setColor(Color.BLACK);
            paint.setTextSize(60);
            String scoreText = "Bounces: " + score;
            float textWidth = paint.measureText(scoreText);
            canvas.drawText(scoreText, (screenWidth - textWidth) / 2, 80, paint);

            // Retrieve and draw the best score.
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int bestScore = prefs.getInt(KEY_BEST_SCORE, 0);
            String bestText = "Best: " + bestScore;
            float bestTextWidth = paint.measureText(bestText);
            canvas.drawText(bestText, (screenWidth - bestTextWidth) / 2, 165, paint);

            if (gameOver) {
                // Draw "Game Over" text.
                paint.setTextSize(100);
                String gameOverText = "Game Over";
                float gameOverWidth = paint.measureText(gameOverText);
                canvas.drawText(gameOverText, (screenWidth - gameOverWidth) / 2, (float) gameAreaHeight / 2, paint);

                // Draw a "Play Again" button.
                String playAgainText = "Play Again";
                paint.setTextSize(60);
                float btnTextWidth = paint.measureText(playAgainText);
                float btnWidth = btnTextWidth + 40;
                float btnHeight = 80;
                float btnLeft = (screenWidth - btnWidth) / 2;
                float btnTop = (float) gameAreaHeight / 2 + 50;
                playAgainButton = new RectF(btnLeft, btnTop, btnLeft + btnWidth, btnTop + btnHeight);

                // Fill the button in red.
                paint.setColor(Color.RED);
                canvas.drawRect(playAgainButton, paint);

                // Draw the "Play Again" text in white.
                paint.setColor(Color.WHITE);
                canvas.drawText(playAgainText, btnLeft + 20, btnTop + btnHeight - 20, paint);
            }

            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }


    private void control() {
        try {
            Thread.sleep(17);
        } catch (InterruptedException e) {
            Log.e("MyApp", "An error occurred", e);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If game over, check for Play Again button tap.
        if (gameOver) {
            if (event.getAction() == MotionEvent.ACTION_DOWN &&
                    playAgainButton != null &&
                    playAgainButton.contains(event.getX(), event.getY())) {
                initGame();
            }
            return true;
        }

        float touchY = event.getY();

        // Condition 1: Touch is near the paddle (using your defined vertical tolerance).
        boolean nearPaddle = touchY >= (paddle.top - TOUCH_VERTICAL_TOLERANCE) &&
                touchY <= (paddle.bottom + TOUCH_VERTICAL_TOLERANCE);

        // Condition 2: Touch is in the bottom toolbar area.
        // (Note: gameAreaHeight is the top boundary of the toolbar.)
        boolean inToolbar = touchY >= gameAreaHeight && touchY <= screenHeight;

        // If the touch is either near the paddle or in the toolbar, move the paddle.
        if (nearPaddle || inToolbar) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    float touchX = event.getX();
                    // Clamp the paddle's center so that it never goes off-screen.
                    float clampedX = Math.max(paddleWidth / 2, Math.min(touchX, screenWidth - paddleWidth / 2));
                    float newLeft = clampedX - paddleWidth / 2;
                    float newRight = clampedX + paddleWidth / 2;
                    paddle.left = newLeft;
                    paddle.right = newRight;
                    break;
            }
        }
        return true;
    }
}
