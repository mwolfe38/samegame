package com.github.mwolfe38.samegame;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.wolfewebservices.samegame.util.SystemUiHider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class Game extends ActionBarActivity implements View.OnClickListener, View.OnLongClickListener {

    private static final String TAG = Game.class.getSimpleName();

    private static final String SS_IS_PLAYING = "IS_PLAYING";
    private static final String SS_LEVEL = "LEVEL";
    private static final String SS_BOMS = "BOMBS";
    private static final String SS_GRID_WIDTH = "GRID_COUNT_WIDTH";
    private static final String SS_GRID_HEIGHT = "GRID_COUNT_HEIGHT";


    private SharedPreferences mPrefs;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;

    private Button mPlayButton;

    private int mMoves;
    private int mGameViewHeight, mGameViewWidth;
    private ViewGroup mContentView, mGridContainer, mWelcomeContainer;
    private View mControlsView;
    private TextView mTitleText, mMsgText, mBombText, mScoreText;
    private TextView mPreviewScreenMsg;


    static class Box {
        int color;
        boolean visited;

        Box(int color) {
            this.color = color;
            visited = false;
        }
    }

    private boolean mIsPlaying;

    private static final int[] colors = new int[]{
            Color.BLUE, Color.GREEN,
            Color.RED, Color.YELLOW,
            Color.CYAN, Color.BLACK,
            Color.GRAY, Color.MAGENTA,
            Color.DKGRAY, Color.WHITE
    };

    private static final int LEVELS_PER_COLOR_INCREASE = 2;
    private static final int MAX_LEVELS = colors.length / LEVELS_PER_COLOR_INCREASE;

    private static final int INITIAL_BOMBS = 2;

    private static int mBombs = INITIAL_BOMBS;
    private static Box[][] mGrid = new Box[12][12];

    private static final int mGridHeight = mGrid.length;
    private static final int mGridWidth = mGrid[0].length;

    private int mLevel = 1;

    private static final int EMPTY_BOX_COLOR = -1;

    private Handler mHandler;
    private Toolbar mToolbar;
    private int mScore = 0;
    private static final String PREF_HIGH_SCORE = "high_score";


    private int mHighScore = 0;
    private List<Pair<Integer, Integer>> mMarkedForDeletion = new ArrayList<Pair<Integer, Integer>>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = getSharedPreferences("app", MODE_PRIVATE);
        mHighScore = mPrefs.getInt(PREF_HIGH_SCORE, 0);
        setContentView(R.layout.activity_game);
        mHandler = new Handler();
        mContentView = (ViewGroup) findViewById(R.id.FullScreenContent);
        mGridContainer = (ViewGroup) findViewById(R.id.GameBoard);
        mWelcomeContainer = (ViewGroup) findViewById(R.id.WelcomeScreen);
        mTitleText = (TextView) findViewById(R.id.Title);
        mPlayButton = (Button) findViewById(R.id.PlayGameButton);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mBombText = (TextView) findViewById(R.id.Bombs);
        mScoreText = (TextView) findViewById(R.id.Score);
        mPreviewScreenMsg = (TextView) findViewById(R.id.PreviewScreenMsg);

        setSupportActionBar(mToolbar);
        mIsPlaying = false;
        if (savedInstanceState != null) {
            mIsPlaying = savedInstanceState.getBoolean(SS_IS_PLAYING);
        }

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.PlayGameButton).setOnClickListener(mOnPlayButtonClickListener);
        updateControls();
        updateHighScoreText();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.new_game:
                quitGame();
                return true;
            case R.id.help:

                return false;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateHighScoreText() {
        mPreviewScreenMsg.setText("High Score: " + mHighScore);
    }
    public void updateControls() {
        if (mIsPlaying) {
            mPlayButton.setText("Quit");
        } else {
            mPlayButton.setText("Play");
        }
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(SS_IS_PLAYING, mIsPlaying);
    }


    private void quitGame() {
        mIsPlaying = false;
        fadeOutGameBoardAndShowWelcome();

    }

    private void startGame() {
        mIsPlaying = true;
        mBombs = INITIAL_BOMBS;
        fadeInGameBoardAndHideWelcome();
        resetGrid();
    }

    private void resetGrid() {
        for (int i = 0; i < mGrid.length; i++) {
            for (int j = 0; j < mGrid[i].length; j++) {
                mGrid[i][j] = new Box(getRandomColor());
            }
        }
        drawGrid();
    }


    private void drawGrid() {

        mGameViewWidth = mGridContainer.getMeasuredWidth() / mGridWidth;
        mGameViewHeight = mGridContainer.getMeasuredHeight() / mGridHeight;
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mGameViewHeight);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(mGameViewWidth, mGameViewHeight);
        final Context ctx = getApplication();
        mGridContainer.removeAllViews();
        for (int i = 0; i < mGrid.length; i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setLayoutParams(containerParams);
            for (int j = 0; j < mGrid[i].length; j++) {
                LinearLayout box = new LinearLayout(ctx);

                box.setLayoutParams(boxParams);
                int color = mGrid[i][j].color;
                if (color == -1) {
                    color = Color.TRANSPARENT;
                }

                if (isMarkedForDeletion(i, j)) {
                    box.setAlpha(0.3f);
                }

                box.setBackgroundColor(color);
                row.addView(box);
                box.setOnClickListener(this);
                box.setOnLongClickListener(this);
                box.setTag("" + i + "|" + j);
            }
            mGridContainer.addView(row);
        }
        mBombText.setText("Bombs: " + mBombs);
        mScoreText.setText("" + mScore);
    }

    private int getRandomColor() {
        int colorCount = 3 + (mLevel - 1);
        return colors[(int) (Math.random() * colorCount)];
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnClickListener mOnPlayButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {


            startGame();

            updateControls();
        }
    };

    @Override
    public boolean onLongClick(View view) {
        Object tag = view.getTag();
        if (mBombs > 0 && tag != null && tag instanceof String) {
            String tagS = (String) tag;
            String parts[] = tagS.split("\\|");
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);
            int color = mGrid[row][col].color;
            if (color != EMPTY_BOX_COLOR) {
                Pair<Integer,Integer> pair = new Pair<>(row,col);
                mMarkedForDeletion.add(pair);
                mBombs--;
                flashMarkedForDeletionThenApplyGravity(row,col);
                return true;
            }
        }
        return false;
    }


    @Override
    public void onClick(View view) {
        Object tag = view.getTag();
        if (tag != null && tag instanceof String) {
            String tagS = (String) tag;
            String parts[] = tagS.split("\\|");
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);
            int color = mGrid[row][col].color;
            if (color != EMPTY_BOX_COLOR && hasAdjacentWithSameColor(row, col)) {
                markAdjacentBoxesWithSameColor(row, col, color);
                mMoves++;
                resetBoxesVisited();
                flashMarkedForDeletionThenApplyGravity(row, col);

            }
        }
    }

    private Runnable mApplyGravityRunnable;

    private void flashMarkedForDeletionThenApplyGravity(final int row, final int col) {
        drawGrid();
        if (mApplyGravityRunnable != null) {
            //if we have one set already, cancel it, run it immediately
            mHandler.removeCallbacks(mApplyGravityRunnable);
            mHandler.post(mApplyGravityRunnable);
        }
        mScore += (mMarkedForDeletion.size() * 10) + ((mMarkedForDeletion.size() / 10)  * 5);
        mApplyGravityRunnable = new Runnable() {
            @Override
            public void run() {
                applyVerticalGravity();
                resetBoxesVisited();
                applyHorizontalGravity();
                drawGrid();
                //if the bottom left square is blank after gravity kicks in, player won game
                checkForWinOrLoss();
                mApplyGravityRunnable = null;
            }
        };

        mHandler.postDelayed(mApplyGravityRunnable, 300);
    }

    private void checkForWinOrLoss() {
        if (mGrid[mGridHeight-1][0].color == EMPTY_BOX_COLOR) {
            playerWins();
        } else if (mBombs == 0){
            boolean foundMove = false;
            for (int i=0;i<mGridHeight;i++) {
                for (int j=0;j<mGridWidth;j++) {
                    if (mGrid[i][j].color != EMPTY_BOX_COLOR) {
                        if (hasAdjacentWithSameColor(i,j)) {
                            foundMove = true;
                            break;
                        }
                    }
                }
            }
            if (!foundMove) {
                gameOverYouLose();
            }
        }


    }

    private void checkHighScore() {
        if (mScore > mHighScore) {
            mPrefs.edit().putInt(PREF_HIGH_SCORE, mScore).commit();
            mHighScore = mScore;
        }
    }
    private void gameOverYouLose() {
        mTitleText.setText("You Lose :-(");
        mLevel = 1;
        mBombs = INITIAL_BOMBS;
        mPlayButton.setText("Play Again");
        checkHighScore();
        mScore = 0;
        fadeOutGameBoardAndShowWelcome();
    }
    private void resetBoxesVisited() {
        for (int i = 0; i < mGridHeight; i++) {
            for (int j = 0; j < mGridWidth; j++) {
                mGrid[i][j].visited = false;
            }
        }
    }


    private void debugPrintGrid(String prefix) {
        StringBuffer logStr = new StringBuffer(mGridWidth * mGridHeight * 3);
        logStr.append(prefix + "\n");
        for (int i = 0; i < mGridHeight; i++) {
            logStr.append("[");
            for (int j = 0; j < mGridWidth; j++) {
                int color = mGrid[i][j].color;
                if (color == EMPTY_BOX_COLOR) {
                    logStr.append("/");
                } else if (color == Color.RED) {
                    logStr.append("R");
                } else if (color == Color.BLUE) {
                    logStr.append("B");
                } else if (color == Color.GREEN) {
                    logStr.append("G");
                } else if (color == Color.BLACK) {
                    logStr.append("K");
                } else if (color == Color.WHITE) {
                    logStr.append("W");
                } else if (color == Color.YELLOW) {
                    logStr.append("Y");
                }
                if (j<mGridWidth-1) {
                    logStr.append(" ");
                }
            }
            logStr.append("]\n");
        }
        Log.d(TAG, "\n" + logStr.toString());
    }

    private void shiftColumnLeft(int column) {
        Log.d(TAG, "shiftLeft(" + column + ")");
        for (int x = column; x < mGridWidth; x++) {
            for (int y = 0; y < mGridHeight; y++) {
                int swapColor = EMPTY_BOX_COLOR;
                if (x + 1 < mGridWidth) {
                    swapColor = mGrid[y][x + 1].color;
                    mGrid[y][x + 1].color = EMPTY_BOX_COLOR;
                }
                mGrid[y][x].color = swapColor;
            }
        }

    }


    private void fadeInGameBoardAndHideWelcome() {
        mGridContainer.setVisibility(View.VISIBLE);
        mGridContainer.animate().alphaBy(1.0f).withEndAction(new Runnable() {
            @Override
            public void run() {
            }
        }).start();
        mWelcomeContainer.animate().translationY(mGameViewHeight).withEndAction(new Runnable() {
            @Override
            public void run() {
                mWelcomeContainer.setVisibility(View.GONE);
            }
        }).start();



    }

    private void fadeOutGameBoardAndShowWelcome() {
        mWelcomeContainer.setVisibility(View.VISIBLE);
        mGridContainer.animate().alphaBy(1.0f).withEndAction(new Runnable() {
            @Override
            public void run() {
                mGridContainer.setVisibility(View.GONE);
            }
        }).start();
        mWelcomeContainer.animate().translationY(-mGameViewHeight).start();
        updateHighScoreText();
    }

    private void playerWins() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mTitleText.setText("You Win!!!");
                mLevel++;
                mPlayButton.setText("Start level " + mLevel);
                checkHighScore();
                mBombs+=2;
                mScore += 250 * mLevel;
                fadeOutGameBoardAndShowWelcome();
            }
        },1000);

    }

    private void applyHorizontalGravity() {
        int bottomRow = mGridHeight - 1;
        for (int i = mGridWidth-2; i >= 0; i--) {
            Box box = mGrid[bottomRow][i];
            if (box.color == EMPTY_BOX_COLOR) {
                shiftColumnLeft(i);
            }
        }
    }

    private void shiftColumnDown(int row, int col) {
        while (row > 0) {
            mGrid[row][col].color = mGrid[row - 1][col].color;
            row--;
        }
        mGrid[0][col].color = EMPTY_BOX_COLOR;
    }

    private boolean isMarkedForDeletion(int row, int col) {
        return findInMarkedForDeletion(row, col) != null;
    }

    private Pair findInMarkedForDeletion(int row, int col) {
        for (Pair pair : mMarkedForDeletion) {
            if (pair.first == row && pair.second == col) {
                return pair;
            }
        }
        return null;
    }



    private void applyVerticalGravity() {
        //sort the points such that we order the ones higher up on the gameboard first
        //this prevents the shifting from overwriting points that would get shifted later
        //simplifying the algorithm considerably
        Collections.sort(mMarkedForDeletion, new Comparator<Pair<Integer, Integer>>() {
            @Override
            public int compare(Pair<Integer, Integer> p1, Pair<Integer, Integer> p2) {
                return p1.first - p2.first;
            }
        });

        for (Pair<Integer,Integer> pair : mMarkedForDeletion) {
            shiftColumnDown(pair.first, pair.second);
        }
        mMarkedForDeletion.clear();
    }

    private boolean isValidCoordinate(int row, int column) {
        return row >= 0 && row < mGridHeight && column >= 0 && column < mGridWidth;
    }

    private Collection<Pair<Integer, Integer>> getAdjacents(int row, int column) {
        //all possible adjacent squares
        List<Pair<Integer, Integer>> pairs = Arrays.asList(
                //top                     left
                new Pair<>(row - 1, column), new Pair<>(row, column - 1),
                //bottom                    right
                new Pair<>(row + 1, column), new Pair<>(row, column + 1));

        //filter out those that aren't valid
        return Collections2.filter(pairs, new Predicate<Pair<Integer, Integer>>() {
            @Override
            public boolean apply(Pair<Integer, Integer> input) {
                return isValidCoordinate(input.first, input.second);
            }
        });
    }

    private boolean hasAdjacentWithSameColor(int row, int column) {
        Collection<Pair<Integer, Integer>> adjacentBoxes = getAdjacents(row, column);
        for (Pair<Integer, Integer> pair : adjacentBoxes) {
            if (mGrid[pair.first][pair.second].color == mGrid[row][column].color) {
                return true;
            }
        }
        return false;
    }

    private void markAdjacentBoxesWithSameColor(int row, int column, int color) {
        Collection<Pair<Integer, Integer>> adjacentBoxes = getAdjacents(row, column);
        for (Pair<Integer, Integer> pair : adjacentBoxes) {
            Box box = mGrid[pair.first][pair.second];
            if (!box.visited && color == box.color) {
                mMarkedForDeletion.add(pair);
                mGrid[pair.first][pair.second].visited = true;
                markAdjacentBoxesWithSameColor(pair.first, pair.second, color);
            }
        }
    }
}
