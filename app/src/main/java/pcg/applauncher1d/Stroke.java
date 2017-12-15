package pcg.applauncher1d;

/**
 * Stores and generates info about a stroke
 * Created by Jason on 7/21/15.
 */

public final class Stroke {
    private float startPoint;
    private float endPoint;
    private double startTime;
    private double endTime;
    public double relLen = 0;

    public Stroke(float startPoint, double startTime) {
        this.startPoint = startPoint;
        this.endPoint = startPoint;
        this.startTime = startTime;
        this.endTime = startTime;
    }

    public void setEndPoint(float endPoint) {
        this.endPoint = endPoint;
    }
    public void setEndTime(double endTime) {
        this.endTime = endTime;
    }

    public float getStartPoint() { return startPoint; }
    public double getStartTime() { return startTime; }
    public double getEndTime() {
        return endTime;
    }

    public double getDuration() {
        return endTime - startTime;
    }

    public float getLength() {
        return Math.abs(endPoint - startPoint);
    }

    public direction getDirection() {
        if (endPoint == startPoint) {
            return direction.nil;
        }
        if (endPoint > startPoint) {
            return direction.forward;
        } else {
            return direction.backward;
        }
    }
}