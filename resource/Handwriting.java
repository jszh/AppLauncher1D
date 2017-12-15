package pcg.temp.applauncher1d;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

enum direction {
    forward, backward, nil
}

/**
 * Main activity
 *
 * Receives and responds to touch events.
 */
public class Handwriting extends Activity {

    private final String actStr = "Gesture-Main";
    private final String initContactStr = "ih";

    private GestureDetector mGestureDetector;
    private AudioManager audio;
    public static Handler handler = new Handler();
    private Thread client;
    private Thread server;

    private GestureView gestureView;
    private TextList letterGroup;
    private TextList wordGroup;
    private MessageView messageView;

    private Letter letterGen;
    private Word wordGen;

    private boolean shouldCallResume = false;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        init();
        shouldCallResume = false;
    }

    private void init() {
        setContentView(R.layout.layout);

        mGestureDetector = createGestureDetector(this);

        gestureView = (GestureView) findViewById(R.id.drawing);
        letterGroup = (TextList) findViewById(R.id.letterGroup);
        letterGroup.setTextSize(48);
        wordGroup = (TextList) findViewById(R.id.wordGroup);
        wordGroup.setTextSize(42);
        wordGroup.setIsSelected(true);
        displayPunctuations();
        messageView = (MessageView) findViewById(R.id.messageView);

        letterGen = new Letter(getApplicationContext());
        wordGen = new Word(getApplicationContext());
        audio = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        startUDPClient(initContactStr + new NetworkHelper(getApplicationContext()).getIPAddress());
        startUDPServer();
    }

    private ArrayList<Point> points = new ArrayList<>();
    private ArrayList<Point> tempPoints = new ArrayList<>();
    private ArrayList<Point> retainedPoints = new ArrayList<>();
    private ArrayList<Stroke> strokes = new ArrayList<>();
    private ArrayList<ArrayList<Stroke>> wordStrokes = new ArrayList<>();
    public static boolean shouldShowSuggestions = true;
    public static boolean isLearning = false;
    public static int correctCount = 0;

    public static final ArrayList<String> punctuations =
            new ArrayList<>(Arrays.asList(".", ",", "?", "!", "'", ":", ";", "\"", "(", ")"));
    public static final ArrayList<String> controlChars = new ArrayList<>(Collections.singletonList("␣"));
    /* "⇪" */

    private final float marginX = 200;
    private Stroke currentStroke = new Stroke(0, 0);

    /**
     * Unit of time for a Point is in seconds.
     */
    private Point currentPoint;
    private Point lastPoint;
    private Point lastDrawnPoint = new Point(0, 0);
    private double virtualT = 0;

    private boolean newSegment = true;
    private boolean pathDidStart = false;
    private boolean didDraw = false;
    private boolean didSelectLastLetter = false;
    private boolean didSelectPunctuation = false;
    private long currentEventT = 0;
    private ArrayList<Integer> exitList = new ArrayList<>();

    /**
     * Control Event: events where user's intention is not inputting text.
     * T stands for time (in milliseconds)
     */
    private long lastControlEventT = 0;
    private long lastScrollEventT = 0;

    private ArrayList<ArrayList<CostRecord>> chars = new ArrayList<>();

    private boolean shouldStopEndOps = false;
    private boolean shouldSelectLetter = false;

    /**
     * Ends the current letter.
     *
     * Will wait 100ms before any operation; in this period, if EndOps#interrupt() was called, the
     * operation will be canceled.
     */
    private Thread endOps = new Thread(new EndOps());
    private Thread letterSel  = new Thread(new LetterSelection());

    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.SWIPE_DOWN) {
                    audio.playSoundEffect(Sounds.SELECTED);
                    toggleHighlight();
//                    exitList.add(1);
//                    if (exitList.size() == 4) {
//                        if (exitList.get(0) == 1 && exitList.get(1) == 0 && exitList.get(2) == 0) {
//                            exitList.clear();
//                            return false;
//                        }
//                    }
                    return true;
                } else if (gesture == Gesture.SWIPE_UP) {
                    delete();
//                    setNewControlEvent();
                    return true;
                }else if (gesture == Gesture.TAP) {
//                    exitList.add(0);
//                    setNewControlEvent();
                    return true;
                } else if (gesture == Gesture.TWO_TAP) {
                    selectFocusItem();
//                    setNewControlEvent();
                    return true;
                }
//                exitList.clear();
                return false;
            }
        });

        gestureDetector.setOneFingerScrollListener(new GestureDetector.OneFingerScrollListener() {
            @Override
            public boolean onOneFingerScroll(float displacement, float delta, float velocity) {

                if (shouldSelectLetter) {
                    if (lastScrollEventT == 0) lastScrollEventT = currentEventT;
                    if (lastControlEventT == 0) lastControlEventT = currentEventT;

                    if (velocity > 1.2 && currentEventT - lastScrollEventT > 125) {
                        select(true);
                        lastScrollEventT = currentEventT;
                    }
                    if (velocity < -1.2 && currentEventT - lastScrollEventT > 125) {
                        select(false);
                        lastScrollEventT = currentEventT;
                    }
                    return true;
                }

                if (currentEventT - lastControlEventT > 200) {
                    if (newSegment) {
                        if (retainedPoints.size() != 0) {
                            prepareForNewPath(retainedPoints.get(0));
                            for (Point point : retainedPoints) {
                                appendPoint(point);
                            }
                            retainedPoints.clear();
                            appendPoint(currentPoint);
                        } else {
                            prepareForNewPath(currentPoint);
                        }
                        newSegment = false;
                    } else {
                        appendPoint(currentPoint);
                    }
                } else {
                    if (newSegment) {
                        retainedPoints.add(currentPoint);
                    }
                }

                exitList.clear();
                return true;
            }
        });

        gestureDetector.setTwoFingerScrollListener(new GestureDetector.TwoFingerScrollListener() {
            @Override
            public boolean onTwoFingerScroll(float displacement, float delta, float velocity) {

                discard();
                currentPoint = null;

                if (lastScrollEventT == 0) lastScrollEventT = currentEventT;
                if (lastControlEventT == 0) lastControlEventT = currentEventT;

                if ((velocity > 1.2 && currentEventT - lastScrollEventT > 120) || (velocity > 6 && currentEventT - lastScrollEventT > 20)) {
                    select(true);
                    lastScrollEventT = currentEventT;
                }
                if ((velocity < -1.2 && currentEventT - lastScrollEventT > 120) || (velocity < -6 && currentEventT - lastScrollEventT > 20)) {
                    select(false);
                    lastScrollEventT = currentEventT;
                }
                setNewControlEvent();

                exitList.clear();
                return true;
            }
        });
        return gestureDetector;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {

        int pointerCount = event.getPointerCount();
        currentEventT = event.getEventTime();

        if (pointerCount == 1) {
            currentPoint = new Point(event.getX(), event.getEventTime() / 1000d);

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
//                endOps.interrupt();
                letterSel.interrupt();
//                if (shouldContinue(currentPoint)) {
//                    shouldStopEndOps = true;
//                } else {
                    newSegment = true;
//                    if (!shouldStopEndOps) {    // endOps not running
//                        endLetter();
//                    }
//                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (currentEventT - lastControlEventT > 200 && !(shouldSelectLetter)) {
                    letterSel.interrupt();
                    shouldStopEndOps = false;
                    shouldSelectLetter = false;
//                    endOps = new Thread(new EndOps());
//                    endOps.start();
                    endLetter();
                }
                if (shouldSelectLetter) {
//                    endOps.interrupt();
                    if (!shouldShowSuggestions) {
                        chars.clear();
                    }
                    if (chars.size() == 0) {
                        selectLetter();
                    } else {
                        logChar(true);
                        ArrayList<CostRecord> temp = new ArrayList<>();
                        CostRecord cr = new CostRecord(letterGroup.getSelectedItem(), 0);   // Sets cost to 0
                        temp.add(cr);
                        chars.add(temp);
                        wordStrokes.add(new ArrayList<>(strokes));
                        setWordDisplay();
                        shouldSelectLetter = false;
                        toggleHighlight();
                    }
//                    setNewControlEvent();
                }
            }
        }

        return mGestureDetector != null && mGestureDetector.onMotionEvent(event);
    }

    /**
     * Responds to the camera key. (Used as delete key)
     *
     * Will first delete letters in the current letter (ArrayList chars), then delete words on
     * messageView.
     * @see MessageView#removeLast()
     *
     * @return true to override camera.
     */
    @Override
    public boolean onKeyDown(int keycode, @NonNull KeyEvent event) {
        if (keycode == KeyEvent.KEYCODE_CAMERA) {
            delete();
            return true;
        }
        return super.onKeyDown(keycode, event);
    }

    @Override
    protected void onPause() {
        startUDPClient("Bye");
        stopUDPServer();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shouldCallResume) {
            startUDPClient(initContactStr + new NetworkHelper(getApplicationContext()).getIPAddress());
            startUDPServer();
        }
        shouldCallResume = true;
    }

    private void startUDPClient(String msg) {
        if (client != null) {
            if (client.isAlive()) client.interrupt();
        }
        client = new Thread(new UDPClient(msg, getApplicationContext()));
        client.start();
    }

    private void startUDPServer() {
        if (server != null) {
            if (!server.isAlive()) {
                server = new Thread(new UDPServer(this));
                server.start();
            }
        } else {
            server = new Thread(new UDPServer(this));
            server.start();
        }
    }

    private void stopUDPServer() {
        UDPServer.stopServer();
    }

    private void select(boolean forward) {
        if (wordGroup.getIsSelected()) {
            wordGroup.select(forward);
        } else {
            letterGroup.select(forward);
        }
    }

    private void delete() {
        if (chars.isEmpty()) {
            String s = messageView.removeLast();
            if (!s.equals("")) {
                LogHelper.writeToLog("d,y," + SystemClock.uptimeMillis() + "," + s);
            }
        } else {
            LogHelper.writeToLog("d,n," + SystemClock.uptimeMillis());
            clearCurrent();
        }
    }

    private void toggleHighlight() {
        if (wordGroup.getIsSelected()) {
            letterGroup.setIsSelected(true);
            wordGroup.setIsSelected(false);
            didSelectLastLetter = true;
        } else {
            wordGroup.setIsSelected(true);
            letterGroup.setIsSelected(false);
        }
    }

    private void setNewControlEvent() {
        lastControlEventT = currentEventT;
        retainedPoints.clear();
        shouldStopEndOps = true;
//        endOps.interrupt();
    }

    private void selectFocusItem() {
        String str;
        audio.playSoundEffect(Sounds.TAP);
        if (wordGroup.getIsSelected()) {
            str = wordGroup.getSelectedItem();
            messageView.addString(str, true);
            if (str.equals("␣")) {
                str = " ";
            }
            LogHelper.writeToLog("s," + currentEventT + "," + str);
        } else {
            str = letterGroup.getSelectedItem();
            didSelectLastLetter = true;
            for (String p : punctuations) {
                if (p.equals(str) || str.equals(" ")) {
                    didSelectPunctuation = true;
                    break;
                }
            }
            if (!didSelectPunctuation) {
                logChar(true);
            } else {
                LogHelper.writeToLog("s," + currentEventT + "," + str);
            }
            messageView.addString(str, false);
        }
        gestureView.clearPath();
        clearWord();
        displayPunctuations();
        setNewControlEvent();
    }

    private void selectLetter() {
        selectFocusItem();
        shouldSelectLetter = false;
        toggleHighlight();
    }

    private void displayPunctuations() {
        letterGroup.setContent(punctuations, 0);
        letterGroup.setHighlightEndIndex(0);
        wordGroup.setContent(controlChars, 0);
    }

    /**
     * Gets the direction of current point in relation to the last point.
     */
    private direction getDirection(Point point) {
        if (!points.isEmpty()) {
            if (point.getX() > lastPoint.getX()) {
                return direction.forward;
            } else {
                return direction.backward;
            }
        }
        return direction.nil;
    }

    /**
     * Did the user unintentionally scroll out of the touch pad?
     *
     * @return True if this point should be added to the last path
     */
    private boolean shouldContinue(Point point) {
        double t = point.getT();
        return (t - lastDrawnPoint.getT() < 0.1) && (t - lastDrawnPoint.getT() >= 0) && (Math.abs(lastDrawnPoint.getX() - point.getX()) < 50);
    }

    // New character
    private void prepareForNewPath(Point point) {
        discard();
        tempPoints.add(point);
        currentStroke = new Stroke(point.getX(), point.getT());
        lastPoint = point;
        lastDrawnPoint = point;
        pathDidStart = false;
        didDraw = false;
        virtualT = 0;

        if (letterGroup.getIsSelected()) {
            if (didSelectLastLetter) {
                didSelectLastLetter = false;
                if (didSelectPunctuation) {
                    toggleHighlight();
                }
            } else {
                toggleHighlight();
            }
        }

        didSelectPunctuation = false;
    }

    /**
     * Handles the drawing and parsing of a new point.
     *
     * If the direction from last point to this point agrees with that of the current stroke, and
     * that the current stroke length exceeds a certain threshold (150 for a new stroke, 50
     * otherwise), this point will be drawn and set to the end point of current stroke (along with
     * other points in tempStroke). Otherwise it will be appended to tempPoints.
     *
     * @param point The newly added point.
     */
    private void appendPoint(Point point) {

        if (currentStroke == null) currentStroke = new Stroke(point.getX(), point.getT());

        if (shouldSelectLetter) return;

        if ((currentStroke.getDirection() != direction.nil) && (getDirection(point) != currentStroke.getDirection())) {
            if (!(currentStroke.getLength() <= 20 || (strokes.size() == 0 && currentStroke.getLength() < 140))) {
                appendCurrentStrokeTo(strokes, false);

                currentStroke = new Stroke(lastPoint.getX(), lastPoint.getT());
                if (!tempPoints.isEmpty()) {
                    for (Point tempPoint : tempPoints) {
                        drawLineTo(tempPoint);
                        points.add(tempPoint);
                    }
                    tempPoints.clear();
                }
                tempPoints.add(point);
            } else {
                // Discard
                currentStroke.setEndPoint(currentStroke.getStartPoint());
                currentStroke.setEndTime(currentStroke.getStartTime());
                tempPoints.clear();
            }
        } else {
            currentStroke.setEndPoint(point.getX());
            currentStroke.setEndTime(point.getT());

            if (currentStroke.getLength() <= 20 || (strokes.size() == 0 && currentStroke.getLength() < 150)) {
                tempPoints.add(point);
            } else {
                if (!pathDidStart) {
                    pathDidStart = true;
                    float y = 234 - currentStroke.getStartPoint() / 6f;
                    gestureView.newPathFrom(marginX, y);
                    letterSel = new Thread(new LetterSelection());
                    letterSel.start();
                }
                if (!tempPoints.isEmpty()) {
                    for (Point tempPoint : tempPoints) {
                        drawLineTo(tempPoint);
                        points.add(tempPoint);
                    }
                    tempPoints.clear();
                }
                drawLineTo(point);
                updateLetters(); // This updates the letter display in real time.
            }
        }

        lastPoint = point;
    }

    /**
     * Draws a line to a given point, then adds that point to points[]
     *
     * @param point The point to which the line will be drawn.
     */
    private void drawLineTo(Point point) {
        float y = 234f - point.getX() / 6f;
        if (point.getT() - lastDrawnPoint.getT() > 0.05) {
            virtualT += 0.05f;
        } else if (point.getT() - lastDrawnPoint.getT() > 0) {
            virtualT += point.getT() - lastDrawnPoint.getT();
        }
        float x = marginX + (float) (virtualT) * 266f;

        gestureView.drawLineTo(x, y);
        lastDrawnPoint = point;
        points.add(point);
        didDraw = true;
    }

    /**
     * Appends the current stroke; gets the current character represent by the strokes
     *
     * @param strokes The List to which the currentStroke will be appended
     * @param didEnd If the stroke is the ending stroke of a stroke sequence (i.e. did we receive
     *               TOUCH_UP event?)
     */
    private void appendCurrentStrokeTo(ArrayList<Stroke> strokes, boolean didEnd) {
        if (currentStroke.getLength() > 0) {
            strokes.add(currentStroke);
        }
        if (strokes.size() > 0) {
            if (strokes.get(0) != null) {
                ArrayList<CostRecord> r = letterGen.lettersFromStrokes(strokes);
                if (didEnd) {
                    chars.add(r);
                }
                setLetterDisplay(r);
            }
        }
    }

    private void logChar(boolean sel) {
        long startTime, endTime;
        if (strokes.size() > 0) {
            startTime = (long) (strokes.get(0).getStartTime() * 1000);
            endTime = (long) (strokes.get(strokes.size() - 1).getEndTime() * 1000);
        } else {
            startTime = (long) (currentStroke.getStartTime() * 1000);
            endTime = (long) (currentStroke.getEndTime() * 1000);
        }
        String msg = "c," + (sel ? "y" : "n") + "," + startTime + "," + endTime + "," + currentEventT + "," + letterGroup.getHighlightLetters() + "," + (letterGroup.getHighlightLetters().equals("") ? "" : letterGroup.getOtherLetters()) + (sel ? "," + letterGroup.getSelectedItem() : "");
        LogHelper.writeToLog(msg);
        Log.v(actStr, "Speed: " + 12f / (currentEventT - startTime));
    }

    private void endLetter() {
//        if (shouldStopEndOps) return;
//        shouldStopEndOps = true;
        if (didDraw) {
            if (!(strokes.size() == 0 && currentStroke.getLength() < 120)) {
                appendCurrentStrokeTo(strokes, true);
            }
            if (strokes.size() > 0) {
                logChar(false);
                wordStrokes.add(new ArrayList<>(strokes));
                setWordDisplay();
            }
        }
        didDraw = false;
        retainedPoints.clear();

        // Experiment related task
        if (isLearning) {
            if (letterGroup.getHighlightLetters().contains(UDPServer.getContent())) {
                correctCount++;
                startUDPClient(String.valueOf(correctCount));
            }
        }
    }

    /**
     * Clears the current input data.
     */
    private void discard() {
        points.clear();
        tempPoints.clear();
        retainedPoints.clear();
        strokes.clear();
    }

    private void updateLetters() {
        ArrayList<Stroke> tempStrokes = new ArrayList<>(strokes);
        tempStrokes.add(currentStroke);
        ArrayList<CostRecord> crs = letterGen.lettersFromStrokes(tempStrokes);
        setLetterDisplay(crs);
    }

    private void setLetterDisplay(ArrayList<CostRecord> crs) {
        ArrayList<String> currentLetters = new ArrayList<>();
        for (CostRecord c : crs) {
            for (int i = 0; i < c.getLetters().length(); i++) {
                currentLetters.add(c.getLetters().substring(i, i + 1));
            }
        }
        letterGroup.setContent(currentLetters, 0);
        Log.v(actStr, String.valueOf(currentLetters));

        // Where should the orange highlight (of the most probable letters) end?
        letterGroup.setHighlightEndIndex(crs.size() != 0 ? crs.get(0).getLetters().length() : 0);
    }

    private void setWordDisplay() {
        ArrayList<String> words;
        if (!shouldShowSuggestions) return;
        words = wordGen.getWords(chars);

        String temp = "";
        if (!words.isEmpty()) {
            if (words.get(0).length() == chars.size()) {
                temp = words.get(0);
            }
            String msg = "w,n,";
            wordGroup.setContent(words, chars.size());
            msg += words;
            LogHelper.writeToLog(msg);
            Log.v(actStr, msg);
        } else {
            words = wordGen.getCorrection(wordStrokes);
            String msg = "w,y,";
            wordGroup.setContent(words, chars.size());
            msg += words;
            LogHelper.writeToLog(msg);
            Log.v(actStr, msg);
        }

        for (int i = temp.length(); i < chars.size(); i++) {
            temp += chars.get(i).get(0).getLetters().substring(0, 1);
        }
        if (!isLearning) {
            messageView.addTempWord(temp, chars.size());
        }
    }

    /**
     * Clears the wordGroup and prepares for a new word.
     */
    private void clearWord() {
        discard();
        chars.clear();
        wordGroup.clear();
        wordStrokes.clear();
    }

    /**
     * Clears the current letter (if any) on letterGroup and wordGroup.
     */
    private void clearCurrent() {
        if (!chars.isEmpty()) {
            chars.remove(chars.size() - 1);
            wordStrokes.remove(wordStrokes.size() - 1);
        }

        points.clear();
        tempPoints.clear();
        retainedPoints.clear();
        gestureView.clearPath();
        pathDidStart = false;

        if (!chars.isEmpty()) {
            if (shouldShowSuggestions) {
                setLetterDisplay(chars.get(chars.size() - 1));
                setWordDisplay();
            } else {
                displayPunctuations();
            }
        } else {
            displayPunctuations();
            messageView.addTempWord("", 0);
        }
    }

    private class EndOps implements Runnable {
        public void run() {
            try {
                Thread.sleep(120);
                if (!shouldStopEndOps) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() { // Back to main thread
                            endLetter();
                        }
                    });
                }
            } catch (InterruptedException ie) {
                Log.v(actStr, "EndOps Interrupted");
            }
        }
    }

    private class LetterSelection implements Runnable {
        public void run() {
            try {
                while (!shouldSelectLetter) {
                    Thread.sleep(50);
                    if (SystemClock.uptimeMillis() / 1000f - lastDrawnPoint.getT() >= 0.3) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!letterGroup.getIsSelected()) toggleHighlight();
                                shouldSelectLetter = true;
                                letterGroup.clearFocusAtIndex(0);
                                letterGroup.setFocus((letterGroup.getHighlightEndIndex() - 1) / 2);
                                audio.playSoundEffect(Sounds.SELECTED);
                            }
                        });
                    }
                }
            } catch (InterruptedException ie) {
                Log.v(actStr, "Letter Selection Interrupted");
            }
        }
    }

}
