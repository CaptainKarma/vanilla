/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.vanilla.android.vanilla;

import android.app.Activity;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.view.GestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.util.ArrayList;

public class SlidingView extends FrameLayout
	implements View.OnTouchListener
	{
	/**
	 * Ignore drag until we made 30 px progress.
	 */
	private final float MAX_PROGRESS = 30;
	/**
	 * Duration of the animate() call in ms
	 */
	private final int ANIMATION_DURATION = 250;
	/**
	 * The maximum (initial) offset of the view
	 */
	private float mMaxOffsetY = 0;
	/**
	 * The previous Y coordinate, used to calculate the movement diff.
	 */
	private float mPreviousY = 0;
	/**
	 * The total progress in pixels of this drag
	 */
	private float mProgressPx = 0;
	/**
	 * Signals the direction and speed of the fling
	 */
	private float mFlingVelocity = 0;
	/**
	 * TRUE if we started to move this view
	 */
	private boolean mDidScroll = false;
	/**
	 * TRUE if we have to hide the slide on visibility changes
	 */
	private boolean mDelayedHide = false;
	/**
	 * Reference to the gesture detector
	 */
	private GestureDetector mDetector;
	/**
	 * An external View we are managing during layout changes.
	 */
	private View mSlaveView;
	/**
	 * The resource id to listen for touch events
	 */
	private int mSliderHandleId = 0;
	/**
	 * Whether or not to have the slider always in expanded mode
	 */
	private boolean mSliderAlwaysExpanded = false;
	/**
	 * The current expansion stage
	 */
	int mCurrentStage = 0;
	/**
	 * List with all possible stages and their offsets
	 */
	ArrayList<Integer> mStages = new ArrayList<Integer>();
	/**
	 * Slide expansion states used by onSlideExpansionChanged
	 */
	public final static int EXPANSION_PARTIAL = 0;
	public final static int EXPANSION_ALWAYS_EXPANDED = 1;
	public final static int EXPANSION_OVERLAY_EXPANDED = 2;
	/**
	 * Our callback interface
	 */
	private Callback mCallback;
	public interface Callback {
		public abstract void onSlideExpansionChanged(int expansion);
	}


	public SlidingView(Context context) {
		this(context, null);
	}

	public SlidingView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public SlidingView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setBackgroundColor(ThemeHelper.fetchThemeColor(context, android.R.attr.colorBackground));

		mDetector = new GestureDetector(new GestureListener());
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlidingViewPreferences);
		mSliderHandleId = a.getResourceId(R.styleable.SlidingViewPreferences_slider_handle_id, 0);
		mSliderAlwaysExpanded = a.getInt(R.styleable.SlidingViewPreferences_slider_always_expanded, 0) != 0;
		int slaveId = a.getResourceId(R.styleable.SlidingViewPreferences_slider_slave_id, 0);
		a.recycle();

		// This is probably a parent view: so we need the context but can search
		// it before we got inflated:
		mSlaveView = ((Activity)context).findViewById(slaveId);
	}

	/**
	 * Sets the callback receiver of this instance.
	 *
	 * @param callback Reference to activity implementing the callback interface
	 */
	public void setCallback(Callback callback) {
		mCallback = callback;
	}

	/**
	 * Fully expands the slide
	 */
	public void expandSlide() {
		setExpansionStage(mStages.size()-1);
	}

	/**
	 * Hides the slide
	 */
	public void hideSlide() {
		setSlaveViewStage(0); // ensure that parent is visible before the animation starts
		setExpansionStage(0);
	}

	/**
	 * Same as hideSlide(), but will fire after we are not
	 * visible anymore
	 */
	public void hideSlideDelayed() {
		mDelayedHide = true;
	}

	/**
	 * Returns true if the slide is in its smallest stage
	 */
	public boolean isMinimized() {
		return mCurrentStage == 0;
	}

	/**
	 * Returns true if the slide is fully expanded
	 */
	public boolean isFullyExpanded() {
		return mCurrentStage == (mStages.size()-1);
	}

	/**
	 * Returns true if the slide can be minimized, that is:
	 * It is not always visible and NOT in stage 0.
	 */
	public boolean isShrinkable() {
		return !mSliderAlwaysExpanded && !isMinimized();
	}

	/**
	 * Transforms to the new expansion state
	 *
	 * @param stage the stage to transform to
	 */
	private void setExpansionStage(int stage) {
		if (mStages.size() < 1)
			return;

		if (mSliderAlwaysExpanded)
			stage = mStages.size() - 1;

		mCurrentStage = stage;
		mDelayedHide = false;

		final int pxOff = mStages.get(stage);
		final int duration = mSliderAlwaysExpanded ? 0 : ANIMATION_DURATION;
		this
			.animate()
			.translationY(pxOff)
			.setDuration(duration)
			.setListener(new AnimationListener())
			.setInterpolator(new DecelerateInterpolator());
	}

	/**
	 * Changes the parent view to fit given stage
	 *
	 * @param stage the stage to transform to
	 */
	private void setSlaveViewStage(int stage) {
		if (mSlaveView == null)
			return;

		int totalOffset = 0;
		for (int i = 0; i <= stage; i++) {
			totalOffset += getChildAt(i).getHeight();
		}
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)mSlaveView.getLayoutParams();
		params.bottomMargin = totalOffset;
		mSlaveView.setLayoutParams(params);
	}

	/**
	 * Called after the view was inflated, binds an onTouchListener to all child
	 * elements of the child view
	 */
	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		View handle = findViewById(mSliderHandleId);

		if (handle != null) {
			if (handle instanceof ViewGroup) {
				ViewGroup group = (ViewGroup)handle;
				for (int i = 0; i < group.getChildCount(); i++) {
					group.getChildAt(i).setOnTouchListener(this);
				}
			} else {
				handle.setOnTouchListener(this);
			}
		}
	}


	/**
	 * Attempts to stack all views orizontally in the available space
	 */
	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		int viewHeight = getMeasuredHeight();
		int childCount = getChildCount();
		int topOffset = 0;
		View lastChild = null;

		mStages.clear();

		for (int i = 0; i < childCount ; i++) {
			lastChild = getChildAt(i);
			int childWidth = lastChild.getMeasuredWidth();
			int childHeight = lastChild.getMeasuredHeight();
			int childBottom = childHeight + topOffset;

			// No child should consume space outside of our view
			if (topOffset > viewHeight)
				topOffset = viewHeight;
			if (childBottom > viewHeight)
				childBottom = viewHeight;

			lastChild.layout(0, topOffset, childWidth, childBottom);
			mStages.add(viewHeight - childBottom);
			topOffset += childHeight;
		}

		if (lastChild != null && mMaxOffsetY == 0) {
			// Sizes are now fixed: Overwrite any (possible) FILL_PARENT or WRAP_CONTENT
			// value with the measured size
			// This should only happen on the first run (mMaxOffsetY == 0)
			for (int i = 0; i < childCount ; i++) {
				View child = getChildAt(i);
				FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)child.getLayoutParams();
				params.height = child.getHeight();
				params.width = child.getWidth();
				child.setLayoutParams(params);
			}
		}

		if (changed) {
			mMaxOffsetY = mStages.get(0);
			setTranslationY(mMaxOffsetY);
			setExpansionStage(0);
		}
	}

	@Override
	protected void onWindowVisibilityChanged(int state) {
		super.onWindowVisibilityChanged(state);
		if (state == View.GONE && mDelayedHide) {
			hideSlide();
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event){
		// The GestureDetector needs a motion event where the raw coordinates
		// equal the 'relative' ones to calculate flings correctly.
		// We are therefore creating a faked motion event because we'd like to
		// use the real one for everything else.
		MotionEvent fakeEvent = MotionEvent.obtain(event);
		fakeEvent.setLocation(event.getRawX(), event.getRawY());
		mDetector.onTouchEvent(fakeEvent);

		float y = event.getRawY();
		float dy = y - mPreviousY;    // diff Y
		float vy = getTranslationY(); // view Y

		if (mSliderAlwaysExpanded)
			dy = 0;

		switch(event.getActionMasked()) {
			case MotionEvent.ACTION_UP : {
				if (mDidScroll) {
					int nstages = mStages.size();
					int tstage = 0;
					// add the amounts of pixels we would progress in HALF of the time of the animation as a virtual progress
					int tbonus = (int)(mFlingVelocity * 0.001 * ANIMATION_DURATION * 0.5);
					for (int i = 0; i < nstages; i++) {
						int csnap = getChildAt(i).getHeight() / 2; // try to 'snap in' at half of this childs height
						if (vy+tbonus-csnap <= mStages.get(i))
							tstage = i;
					}
					setExpansionStage(tstage);
				}
				break;
			}
			case MotionEvent.ACTION_DOWN : {
				mProgressPx = 0;
				mFlingVelocity = 0;
				mDidScroll = false;
				break;
			}
			case MotionEvent.ACTION_MOVE : {
				mProgressPx += Math.abs(dy);
				float usedY = vy + dy;

				if (usedY < 0)
					usedY = 0;
				if (usedY > mMaxOffsetY)
					usedY = mMaxOffsetY;

				if (mProgressPx < MAX_PROGRESS) {
					// we did not reach a minimum of progress: do not scroll yet
					usedY = vy;
				} else if (!mDidScroll) {
					mDidScroll = true;
					// cancel this event and let the view know:
					event.setAction(MotionEvent.ACTION_CANCEL);
					v.onTouchEvent(event);
					// parent can use full view, will be reset on ACTION_UP handlers.
					setSlaveViewStage(0);
				}

				setTranslationY(usedY);
				break;
			}
		}
		mPreviousY = y;
		return mDidScroll;
	}

	class GestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent event1, MotionEvent event2,  float velocityX, float velocityY) {
			mFlingVelocity = velocityY;
			return true;
		}
	}

	class AnimationListener extends AnimatorListenerAdapter {
		@Override
		public void onAnimationEnd(Animator animation) {
			setSlaveViewStage(mCurrentStage);
			if (mCallback != null) {
				int expansion = EXPANSION_PARTIAL;
				if (mCurrentStage == mStages.size() -1) {
					expansion = (mSliderAlwaysExpanded ? EXPANSION_ALWAYS_EXPANDED : EXPANSION_OVERLAY_EXPANDED);
				}
				mCallback.onSlideExpansionChanged(expansion);
			}
		}
		@Override
		public void onAnimationCancel(Animator animation) {
			onAnimationEnd(animation);
		}
	}

}
