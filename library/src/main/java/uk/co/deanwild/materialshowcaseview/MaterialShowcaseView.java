package uk.co.deanwild.materialshowcaseview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;


/**
 * Created by deanwild on 04/08/15.
 */
public class MaterialShowcaseView extends FrameLayout implements View.OnTouchListener, View.OnClickListener {
    private Activity mActivity;
    private int mOldHeight;
    private int mOldWidth;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Paint mEraser;
    private Target mTarget;
    private int mXPosition;
    private int mYPosition;
    private CharSequence content;
    private CharSequence title;
    private int mRadius = 200;
    private boolean mUseAutoRadius = true;
    private View mContentBox;
    private TextView mContentTextView;
    private TextView mDismissButton;
    private int mGravity;
    private int mContentBottomMargin;
    private int mContentTopMargin;
    private boolean mDismissOnTouch = false;
    private CharSequence mContentText;
    private CharSequence mDismissText;
    private boolean mShouldRedraw = true;
    private boolean mShouldRender = false; // flag to decide when we should actually render
    private int mMaskColour;

    public MaterialShowcaseView(Context context) {
        super(context);
        init(context);
    }

    public MaterialShowcaseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MaterialShowcaseView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public MaterialShowcaseView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }


    private void init(Context context) {
        setWillNotDraw(false);
        mActivity = (Activity) context;

        // make sure we add a global layout listener so we can adapt to changes
        getViewTreeObserver().addOnGlobalLayoutListener(new UpdateOnGlobalLayout());

        // consume touch events
        setOnTouchListener(this);


        View contentView = LayoutInflater.from(getContext()).inflate(R.layout.showcase_content, this, true);
        mContentBox = contentView.findViewById(R.id.content_box);
        mContentTextView = (TextView) contentView.findViewById(R.id.tv_content);
        mDismissButton = (TextView) contentView.findViewById(R.id.tv_dismiss);
        mDismissButton.setOnClickListener(this);

        mMaskColour = Color.parseColor("#bb335075");
    }


    /**
     * Interesting drawing stuff.
     * We draw a block of semi transparent colour to fill the whole screen then we draw of transparency
     * to create a circular "viewport" through to the underlying content
     *
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // don't bother drawing if we're not ready
        if(!mShouldRender) return;

        // get current dimensions
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        // build a new canvas if needed i.e first pass or new dimensions
        if (mBitmap == null || mCanvas == null || mOldHeight != height || mOldWidth != width) {
            mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
        }

        // save our 'old' dimensions
        mOldWidth = width;
        mOldHeight = height;

        // clear canvas
        mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // draw solid background
        mCanvas.drawColor(mMaskColour);

        // Erase a circle
        if (mEraser == null) {
            mEraser = new Paint();
            mEraser.setColor(0xFFFFFFFF);
            mEraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            mEraser.setFlags(Paint.ANTI_ALIAS_FLAG);
        }
        mCanvas.drawCircle(mXPosition, mYPosition, mRadius, mEraser);

        // Draw the bitmap on our views  canvas.
        canvas.drawBitmap(mBitmap, 0, 0, null);

        applyContentText();
        applyDismissText();

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mDismissOnTouch) {
            removeFromWindow();
        }
        return true;
    }

    /**
     * Dismiss button clicked
     *
     * @param v
     */
    @Override
    public void onClick(View v) {
        removeFromWindow();
    }

    public void removeFromWindow() {
        if (getParent() != null && getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }

        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
    }


    /**
     * Tells us about the "Target" which is the view we want to anchor to.
     * We figure out where it is on screen and (optionally) how big it is.
     * We also figure out whether to place our content and dismiss button above or below it.
     *
     * @param target
     */
    public void setTarget(Target target) {
        mTarget = target;

        if (mTarget != null) {

            // apply the target position
            Point targetPoint = mTarget.getPoint();
            setPosition(targetPoint);

            // apply auto radius
            if (mUseAutoRadius) {
                setRadius(mTarget.getRadius());
            }

            // now figure out whether to put content above or below it
            int height = getMeasuredHeight();
            int midPoint = height / 2;
            int yPos = targetPoint.y;

            if (yPos > midPoint) {
                // target is in lower half of screen, we'll sit above it
                mContentTopMargin = 0;
                mContentBottomMargin = (height - yPos) + mRadius;
                mGravity = Gravity.BOTTOM;
            } else {
                // target is in upper half of screen, we'll sit below it
                mContentTopMargin = yPos + mRadius;
                mContentBottomMargin = 0;
                mGravity = Gravity.TOP;
            }

            applyLayoutParams();

        }
    }

    private void applyLayoutParams() {

        if (mContentBox != null && mContentBox.getLayoutParams() != null) {
            FrameLayout.LayoutParams contentLP = (LayoutParams) mContentBox.getLayoutParams();
            contentLP.bottomMargin = mContentBottomMargin;
            contentLP.topMargin = mContentTopMargin;
            contentLP.gravity = mGravity;
            mContentBox.setLayoutParams(contentLP);
        }
    }

    private void applyContentText() {
        if (mContentTextView != null && !mContentTextView.getText().equals(mContentText)) {
            mContentTextView.setText(mContentText);
        }
    }

    private void applyDismissText(){
        if (mDismissButton != null && !mDismissButton.getText().equals(mDismissText)) {
            mDismissButton.setText(mDismissText);
        }
    }

    @Override
    public void invalidate() {
        mShouldRedraw = true;
        super.invalidate();
    }

    /**
     * SETTERS
     */

    void setPosition(Point point) {
        setPosition(point.x, point.y);
    }

    void setPosition(int x, int y) {
        mXPosition = x;
        mYPosition = y;
    }

    private void setContentText(CharSequence contentText) {
        mContentText = contentText;
        applyContentText();
    }

    private void setDismissText(CharSequence dismissText) {
        mDismissText = dismissText;
        applyDismissText();
    }

    private void setDismissOnTouch(boolean dismissOnTouch) {
        mDismissOnTouch = dismissOnTouch;
    }

    private void setUseAutoRadius(boolean useAutoRadius) {
        mUseAutoRadius = useAutoRadius;
    }

    private void setRadius(int radius) {
        mRadius = radius;

    }

    private void setShouldRender(boolean shouldRender) {
        mShouldRender = shouldRender;
    }

    private void setMaskColour(int maskColour) {
        mMaskColour = maskColour;
    }

    /**
     * REDRAW LISTENER - this ensures we redraw after activity finishes laying out
     */
    private class UpdateOnGlobalLayout implements ViewTreeObserver.OnGlobalLayoutListener {

        @Override
        public void onGlobalLayout() {
            if (mShouldRedraw) {
                mShouldRedraw = false;
                setTarget(mTarget);
            }
        }
    }


    /**
     * BUILDER CLASS
     * Gives us a builder utility class with a fluent API for eaily configuring showcase views
     */
    public static class Builder {
        final MaterialShowcaseView showcaseView;

        private final Activity activity;

        public Builder(Activity activity) {
            this.activity = activity;

            showcaseView = new MaterialShowcaseView(activity);
        }

        /**
         * Set the title text shown on the ShowcaseView.
         */
        public Builder setTarget(View target) {
            showcaseView.setTarget(new ViewTarget(target));
            return this;
        }

        /**
         * Set the title text shown on the ShowcaseView.
         */
        public Builder setDismissText(int resId) {
            return setDismissText(activity.getString(resId));
        }

        public Builder setDismissText(CharSequence dismissText) {
            showcaseView.setDismissText(dismissText);
            return this;
        }

        /**
         * Set the title text shown on the ShowcaseView.
         */
        public Builder setContentText(int resId) {
            return setContentText(activity.getString(resId));
        }

        /**
         * Set the descriptive text shown on the ShowcaseView.
         */
        public Builder setContentText(CharSequence text) {
            showcaseView.setContentText(text);
            return this;
        }


        /**
         * Use auto radius, if true then the showcase circle will auto size based on the target view
         * Defaults to true
         */
        public Builder setUseAutoRadius(boolean useAutoRadius) {
            showcaseView.setUseAutoRadius(useAutoRadius);
            return this;
        }

        /**
         * Manually define a radius in pixels - should set setUseAutoRadius to false
         * Defaults to 200 pixels
         */
        public Builder setRadius(int radius) {
            showcaseView.setRadius(radius);
            return this;
        }

        public Builder setDismissOnTouch(boolean dismissOnTouch) {
            showcaseView.setDismissOnTouch(dismissOnTouch);
            return this;
        }

        public Builder setMaskColour(int maskColour) {
            showcaseView.setMaskColour(maskColour);
            return this;
        }

        public MaterialShowcaseView build() {
            showcaseView.setShouldRender(true);

            ViewGroup vg = ((ViewGroup) activity.getWindow().getDecorView());

            ((ViewGroup) activity.getWindow().getDecorView()).addView(showcaseView);
            return showcaseView;
        }
    }

}