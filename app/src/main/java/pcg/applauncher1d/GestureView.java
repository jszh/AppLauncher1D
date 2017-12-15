package pcg.applauncher1d;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.content.Context;
import android.util.AttributeSet;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Displays the gesture the user performed in the center of the screen, on top of the message body.
 * Created by Jason on 7/20/15.
 */

public class GestureView extends View {
    public GestureView(Context context, AttributeSet attrs){
        super(context, attrs);
        setupDrawing();
    }

    private Path drawPath;
    private Paint drawPaint;

    private void setupDrawing(){
        Log.v("Gesture-GView", "drawing setup");

        drawPath = new Path();
        drawPaint = new Paint();
        drawPaint.setColor(0xffffba84);

        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(7);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        drawPaint.setShadowLayer(15, 0, 0, Color.BLACK);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void drawLineTo(float X, float Y) {
        drawPath.lineTo(X, Y);
        this.invalidate();
    }

    public void quadTo(float X1, float Y1, float X, float Y) {
        drawPath.quadTo(X1, Y1, X, Y);
        this.invalidate();
    }

    public void clearPath() {
        drawPath.reset();
        this.invalidate();
    }

    public void newPathFrom(float X, float Y) {
        drawPath.reset();
        drawPath.moveTo(X, Y);
        this.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(drawPath, drawPaint);
    }

}
