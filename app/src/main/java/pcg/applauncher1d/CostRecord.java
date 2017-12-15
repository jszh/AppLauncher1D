package pcg.applauncher1d;

/**
 * A record for a set of letters and its cost
 * Created by Jason on 7/28/15.
 */
public class CostRecord {
    private double cost;
    private String letters;

    public CostRecord(String letters, double cost) {
        this.letters = letters;
        this.cost = cost;
    }

    public String getLetters() {
        return letters;
    }

    public double getCost() {
        return cost;
    }
}
