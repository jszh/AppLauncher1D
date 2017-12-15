package pcg.applauncher1d;

/**
 * Calculates distribution for a given stroke and template.
 * Created by Jason on 7/27/15.
 */
public class Distribution {

    private static double getProbAt(double x, double a, double b, double p, double q, double beta) {
        double width = 0.02;
        x = Math.floor((x - 0.00001) * 50) / 50;
        return (a < x && x < b) ? Math.log(Math.pow(x-a, p-1) * Math.pow(b-x, q-1) / beta / Math.pow(b-a, p+q-1) * width * (b - a)) : -100;
    }

    public static double GetProb(double relLen, double refLen) {
        double prob;
        if (Math.abs(refLen - 1) < 0.01) {
            prob = getProbAt(relLen, 0.1025057, 1, 5.2284703, 1.0921495, 0.15531279);
        } else if (Math.abs(refLen - 0.54) < 0.01) {
//            prob = getProbAt(relLen, 0.1084559, 0.982808, 4.1423868, 4.1323976, 0.0057993037);
            prob = getProbAt(relLen, 0.0744186, 0.9911139, 2.5184808, 2.8704103, 0.0547297303);
        } else {
            prob = getProbAt(relLen, 0, 0.9625, 1.4247099, 14.99557, 0.018345811);
        }
        return prob;
    }

}
