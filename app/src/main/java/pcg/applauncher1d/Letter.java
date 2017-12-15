package pcg.applauncher1d;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Manages letter recognition.
 * Created by Jason on 7/21/15.
 */

class CostComparator implements Comparator<CostRecord> {
    @Override
    public int compare(CostRecord cr1, CostRecord cr2) {
        return cr1.getCost() > cr2.getCost() ? -1 : (cr1.getCost() == cr2.getCost() ? 0 : 1);
    }
}

class StrokeInfo {
    private String letters;
    private double refLen;
    private direction dir;

    public StrokeInfo(String letters, double rLen, direction dir) {
        this.letters = letters;
        this.refLen = rLen;
        this.dir = dir;
    }

    public String getLetters() {
        return letters;
    }

    public direction getDir() {
        return dir;
    }

    public double getRefLen() {
        return refLen;
    }
}

public class Letter {

    private static ArrayList<ArrayList<StrokeInfo>> letters = new ArrayList<>();
    private static HashMap<Character, ArrayList<StrokeInfo>> strokeForChar = new HashMap<>();

    private static Context context;
    public Letter(Context context) {
        Letter.context = context;
        if (!didInit) {
            init();
            didInit = true;
        }
    }

    private static boolean didInit = false;

    private static void init() {
        Log.v("Gesture-Letter", "init");
        try {
            AssetManager am = context.getAssets();
            InputStream is = am.open("letter.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            String line;
            while ((line = reader.readLine()) != null) {
                String[] elements = line.split(" ");
                String str = elements[0];
                char[] seq = elements[1].toCharArray();

                ArrayList<StrokeInfo> ref = new ArrayList<>();

                for (char ch : seq) {
                    double rLen;
                    direction dir;
                    if (ch == 'b' || ch == 'B')
                        dir = direction.backward;
                    else
                        dir = direction.forward;
                    if (ch == 'b' || ch == 'f')
                        rLen = 0.54;
                    else
                        rLen = 1;
                    ref.add(new StrokeInfo(str, rLen, dir));
                }

                letters.add(ref);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        for (ArrayList<StrokeInfo> letterInfo : letters) {
            for (char ch : letterInfo.get(0).getLetters().toCharArray()) {
                strokeForChar.put(ch, letterInfo);
            }
        }
    }

    private static ArrayList<StrokeInfo> getStrokeInfoForChar(char ch) {
        return strokeForChar.get(ch);
    }

    public static String getLettersForChar(char ch) {
        ArrayList<StrokeInfo> letterInfo = getStrokeInfoForChar(ch);
        if (letterInfo != null) {
            return letterInfo.get(0).getLetters();
        }
        return "";
    }

    public static double getDistanceForLetters(char ch, ArrayList<Stroke> strokes) {
        int i = 0;  // current
        int j = 0;  // ref
        double cost = 0;

        ArrayList<StrokeInfo> letterInfo = getStrokeInfoForChar(ch);

        while (i < strokes.size() || j < letterInfo.size()) {
            if (i < strokes.size() && j < letterInfo.size()) {
                if (strokes.get(i).getDirection() == letterInfo.get(j).getDir()) {  // Direction Match
                    cost += Math.abs(strokes.get(i).relLen - letterInfo.get(j).getRefLen());
                    i++;
                    j++;
                } else {
                    cost += strokes.get(i).relLen;
                    if (strokes.get(i).relLen > 0.15) {
                        cost += strokes.get(i).relLen;  // Penalty
                    }
                    i++;
                }
            } else {
                if (i < strokes.size()) {
                    cost += strokes.get(i).relLen;
                    if (strokes.get(i).relLen > 0.15) {
                        cost += strokes.get(i).relLen;  // Penalty
                    }
                    i++;
                }
                if (j < letterInfo.size()) {
                    cost += letterInfo.get(j).getRefLen();
                    j++;
                }
            }
        }

//        cost = Math.abs(strokes.size() - letterInfo.size());
        return cost;
    }

    private double getCost(ArrayList<Stroke> strokes, ArrayList<StrokeInfo> letterInfo) {
        int i = 0;  // current
        int j = 0;  // ref
        double cost = 0;

        if (strokes.size() < letterInfo.size()) {
            return -1000d;
        }

        while (i < strokes.size() || j < letterInfo.size()) {
            if (i < strokes.size() && j < letterInfo.size()) {
                if (strokes.get(i).getDirection() == letterInfo.get(j).getDir()) {
                    cost += Distribution.GetProb(strokes.get(i).relLen, letterInfo.get(j).getRefLen());
                    i++;
                    j++;
                } else {
                    cost += Distribution.GetProb(strokes.get(i).relLen, 0);
                    i++;
                }
            } else {
                if (i < strokes.size()) {
                    cost += Distribution.GetProb(strokes.get(i).relLen, 0);
                    i++;
                }
                if (j < letterInfo.size()) {
                    cost += -100d;
                    j++;
                }
            }
        }

        return cost;
    }

    public ArrayList<CostRecord> lettersFromStrokes(ArrayList<Stroke> strokes) {

        float maxStrokeLen = 0;
        for (Stroke stroke : strokes) {
            float currentLen = stroke.getLength();
            if (currentLen > maxStrokeLen) maxStrokeLen = currentLen;
        }

        for (Stroke stroke : strokes) {
            stroke.relLen = stroke.getLength() / maxStrokeLen;
        }

        ArrayList<CostRecord> costs = new ArrayList<>();

        for (ArrayList<StrokeInfo> letterInfo : letters) {
            double cost = getCost(strokes, letterInfo);
            costs.add(new CostRecord(letterInfo.get(0).getLetters(), cost));
        }

        Collections.sort(costs, new CostComparator());

        ArrayList<CostRecord> r = new ArrayList<>();
        if (costs.get(0).getCost() > -100) {
            r.add(costs.get(0));
            int i = 1;
            while (costs.get(i).getCost() > -100 && costs.get(0).getCost() - costs.get(i).getCost() < 3.5) {
                r.add(costs.get(i));
                i++;
                if (i >= costs.size()) break;
            }
        } else if (costs.get(0).getCost() > -1000) {
            r.add(costs.get(0));
        }

        String str = "";
        boolean shouldWarn = false;
        for (CostRecord c : r) {
            str += c.getLetters() + ", " + c.getCost() + "; ";
            if (c.getCost() <= -100) shouldWarn = true;
        }
//        if (shouldWarn) {
//            Log.e("Gesture-Letter", str);
//        } else {
//            Log.v("Gesture-Letter", str);
//        }

        return r;
    }

}
