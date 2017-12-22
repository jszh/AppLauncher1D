package pcg.applauncher1d;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.content.ContentValues.TAG;

enum direction {
    forward, backward, nil
}

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class LauncherActivity extends AppCompatActivity implements GestureDetector.OnGestureListener {

    private View mContentView;
    private ArrayList<PInfo> mPackages;
    private GestureDetectorCompat mDetector;
    private GestureView gestureView;


    // From 1D Handwriting
    private ArrayList<Point> points = new ArrayList<>();
    private ArrayList<Point> tempPoints = new ArrayList<>();
    private ArrayList<Point> retainedPoints = new ArrayList<>();
    private ArrayList<Stroke> strokes = new ArrayList<>();
    private ArrayList<ArrayList<Stroke>> wordStrokes = new ArrayList<>();
    public static boolean shouldShowSuggestions = true;

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
    private long currentEventT = 0;

    /**
     * Control Event: events where user's intention is not inputting text.
     * T stands for time (in milliseconds)
     */
    private long lastControlEventT = 0;
    private long lastScrollEventT = 0;

    private ArrayList<ArrayList<CostRecord>> chars = new ArrayList<>();


    private Letter letterGen;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set UI layout
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_launcher);
//        mContentView = findViewById(R.id.dummy);
        gestureView = (GestureView) findViewById(R.id.gestureView);

        // setup 1D Handwriting
        letterGen = new Letter(getApplicationContext());
        mDetector = new GestureDetectorCompat(this, this);

        // get a list of apps
        mPackages = getPackages();


    }

    /**
     * Handle touch event
     * @param event
     * @return true if this event is handled by 1D Handwriting
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        currentEventT = event.getEventTime();
        currentPoint = new Point(-event.getY(), currentEventT / 1000d);

        if (!mDetector.onTouchEvent(event)) {

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                retainedPoints.add(currentPoint);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Log.v(TAG, "up, " + currentEventT);
                endLetter();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        Log.v(TAG, "down, " + currentEventT);
        newSegment = true;
        retainedPoints.clear();
        retainedPoints.add(currentPoint);
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX,
            float distanceY) {
//        Log.d(TAG, "onScroll: " + event1.toString() + event2.toString());
//        Log.d(TAG, currentPoint.getX() + "," + currentPoint.getT());

        if (currentEventT - lastControlEventT > 100) {
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

        return true;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {
    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {
    }

    private void setNewControlEvent() {
        lastControlEventT = currentEventT;
        retainedPoints.clear();
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
        } else {
            startTime = (long) (currentStroke.getStartTime() * 1000);
        }
        Log.v(TAG, "Duration: " + (currentEventT - startTime) + " ms");
    }

    private void endLetter() {
        if (didDraw) {
            if (!(strokes.size() == 0 && currentStroke.getLength() < 120)) {
                appendCurrentStrokeTo(strokes, true);
            }
            if (strokes.size() > 0) {
                logChar(false);
                wordStrokes.add(new ArrayList<>(strokes));
                openApp();
            }
        }
        didDraw = false;
        retainedPoints.clear();

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
                openApp();
            }
        }
    }


    // Log the letters
    private void setLetterDisplay(ArrayList<CostRecord> crs) {
        ArrayList<String> currentLetters = new ArrayList<>();
        for (CostRecord c : crs) {
            for (int i = 0; i < c.getLetters().length(); i++) {
                currentLetters.add(c.getLetters().substring(i, i + 1));
            }
        }
//        letterGroup.setContent(currentLetters, 0);
        Log.v(TAG, String.valueOf(currentLetters));

    }

    // TODO
    private void openApp() {
        ArrayList<CostRecord> crs = chars.get(chars.size() - 1);
        if(!crs.isEmpty()) {
            char topChar = crs.get(0).getLetters().charAt(0);
            Log.v(TAG, "Top Letter: " + topChar);

            switch (topChar){
                case 'w':
                    launchActivityFromPackage(getPInfo("微信"));
                    break;
                case 'p':
                    launchActivityFromPackage(getPInfo("PDF Reader"));
                    break;
                case 'd':
                    launchActivityFromPackage(getPInfo("豆瓣"));
                    break;
                default:
                    break;
            }
        }
    }

    // search PInfo by app name
    private PInfo getPInfo(String appname) {
        for (PInfo pInfo : mPackages) {
            if(pInfo.appname.equals(appname)){
                return pInfo;
            }
        }
        return mPackages.get(0);
    }


    // Application package info
    class PInfo {
        private String appname = "";
        private String pname = "";
        private String versionName = "";
        private Drawable icon;
        private void prettyPrint() {
            if (!appname.equals(pname)) {
                Log.v(TAG, appname + "\t" + pname + "\t" + versionName);
            }
        }
    }

    /**
     * launches an app from the given package info
     * https://stackoverflow.com/questions/3872063/launch-an-application-from-another-application-on-android
     */
    private void launchActivityFromPackage(PInfo p) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(p.pname);
        if (launchIntent != null) { //null pointer check in case package name was not found
            startActivity(launchIntent);
        }
    }

    /**
     * get all packages installed on this device
     * https://stackoverflow.com/questions/2695746/how-to-get-a-list-of-installed-android-applications-and-pick-one-to-run
     */
    private ArrayList<PInfo> getPackages() {
        ArrayList<PInfo> apps = getInstalledApps(false); /* false = no system packages */
        for (int i = 0; i < apps.size(); i++) {
            apps.get(i).prettyPrint();
        }
        return apps;
    }

    private ArrayList<PInfo> getInstalledApps(boolean getSysPackages) {
        ArrayList<PInfo> res = new ArrayList<>();
        List<PackageInfo> packs = getPackageManager().getInstalledPackages(0);
        for(int i = 0; i < packs.size(); i++) {
            PackageInfo p = packs.get(i);
            if ((!getSysPackages) && ((p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)) {  // is system app
                continue;
            }
            PInfo newInfo = new PInfo();
            newInfo.appname = p.applicationInfo.loadLabel(getPackageManager()).toString();
            newInfo.pname = p.packageName;
            newInfo.versionName = p.versionName;
            newInfo.icon = p.applicationInfo.loadIcon(getPackageManager());
            res.add(newInfo);
        }
        return res;
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

}
