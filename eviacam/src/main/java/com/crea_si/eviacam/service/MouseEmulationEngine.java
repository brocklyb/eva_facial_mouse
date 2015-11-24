/*
 * Enable Viacam for Android, a camera based mouse emulator
 *
 * Copyright (C) 2015 Cesar Mauri Loba (CREA Software Systems)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.crea_si.eviacam.service;

import com.crea_si.eviacam.EVIACAM;
import com.crea_si.eviacam.api.IMouseEventListener;

import android.accessibilityservice.AccessibilityService;
import android.app.Service;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

public class MouseEmulationEngine implements MotionProcessor {
    // layer for drawing the pointer and the dwell click feedback
    private PointerLayerView mPointerLayer;
    
    // layer for drawing the docking panel
    private DockPanelLayerView mDockPanelView;

    // layer for the scrolling user interface
    private ScrollLayerView mScrollLayerView;
    
    // layer for drawing different controls
    private ControlsLayerView mControlsLayer;
    
    // object which provides the logic for the pointer motion and actions 
    private PointerControl mPointerControl;
    
    // dwell clicking function
    private DwellClick mDwellClick;
    
    // perform actions on the UI using the accessibility API
    private AccessibilityAction mAccessibilityAction;
    
    // event listener
    IMouseEventListener mMouseEventListener;
    
    public MouseEmulationEngine(Service s, OverlayView ov) {
        /*
         * UI stuff 
         */
        if (s instanceof AccessibilityService) {
            mDockPanelView= new DockPanelLayerView(s);
            ov.addFullScreenLayer(mDockPanelView);
        }

        mScrollLayerView= new ScrollLayerView(s);
        ov.addFullScreenLayer(mScrollLayerView);
        
        mControlsLayer= new ControlsLayerView(s);
        ov.addFullScreenLayer(mControlsLayer);
        
        // pointer layer (should be the last one)
        mPointerLayer= new PointerLayerView(s);
        ov.addFullScreenLayer(mPointerLayer);

        /*
         * control stuff
         */
        mPointerControl= new PointerControl(s, mPointerLayer);
        
        mDwellClick= new DwellClick(s);
        
        if (s instanceof AccessibilityService) {
            mAccessibilityAction= new AccessibilityAction ((AccessibilityService) s, 
                    mControlsLayer, mDockPanelView, mScrollLayerView);
        }
    }
    
    @Override
    public void pause() {
        mPointerLayer.setVisibility(View.INVISIBLE);
        mScrollLayerView.setVisibility(View.INVISIBLE);
        mControlsLayer.setVisibility(View.INVISIBLE);
        if (mDockPanelView!= null) mDockPanelView.setVisibility(View.INVISIBLE);
    }
    
    @Override
    public void resume() {
        mPointerControl.reset();
        mDwellClick.reset();
        if (mAccessibilityAction!= null) mAccessibilityAction.reset();

        if (mDockPanelView!= null) mDockPanelView.setVisibility(View.VISIBLE);
        mControlsLayer.setVisibility(View.VISIBLE);
        mScrollLayerView.setVisibility(View.VISIBLE);
        mPointerLayer.setVisibility(View.VISIBLE);
    }    
    
    @Override
    public void cleanup() {
        if (mAccessibilityAction!= null) {
            mAccessibilityAction.cleanup();
            mAccessibilityAction= null;
        }

        mDwellClick.cleanup();
        mDwellClick= null;

        mPointerControl.cleanup();
        mPointerControl= null;

        // nothing to be done for mScrollLayerView

        if (mDockPanelView!= null) {
            mDockPanelView.cleanup();
            mDockPanelView= null;
        }

        mControlsLayer.cleanup();
        mControlsLayer= null;

        mPointerLayer.cleanup();
        mPointerLayer= null;
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mAccessibilityAction != null) mAccessibilityAction.onAccessibilityEvent(event);
    }

    public boolean registerListener(IMouseEventListener l) {
        if (mMouseEventListener== null) {
            mMouseEventListener= l;
            return true;
        }
        return false;
    }

    public void unregisterListener() {
        mMouseEventListener= null;
    }

    /*
     * Last values for checkAndSendEvents
     */
    private Point mLastPos= new Point();
    private boolean mLastClicked= false;

    /*
     * Sent mouse events when needed
     */
    private void checkAndSendEvents(Point pos, boolean clicked) {
        final float DEFAULT_SIZE = 1.0f;
        final int DEFAULT_META_STATE = 0;
        final float DEFAULT_PRECISION_X = 1.0f;
        final float DEFAULT_PRECISION_Y = 1.0f;
        final int DEFAULT_DEVICE_ID = 0;
        final int DEFAULT_EDGE_FLAGS = 0;

        // Check and generate events
        IMouseEventListener l= mMouseEventListener;
        if (l== null) return;

        try {
            long now = SystemClock.uptimeMillis();

            if (!pos.equals(mLastPos)) {
                MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE,
                    pos.x, pos.y, 0.0f, DEFAULT_SIZE, DEFAULT_META_STATE,
                    DEFAULT_PRECISION_X, DEFAULT_PRECISION_Y, DEFAULT_DEVICE_ID,
                    DEFAULT_EDGE_FLAGS);
                event.setSource(InputDevice.SOURCE_CLASS_POINTER);
                l.onMouseEvent(event);
            }
            if (mLastClicked && !clicked) {
                MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                        pos.x, pos.y, 0.0f, DEFAULT_SIZE, DEFAULT_META_STATE,
                        DEFAULT_PRECISION_X, DEFAULT_PRECISION_Y, DEFAULT_DEVICE_ID,
                        DEFAULT_EDGE_FLAGS);
                event.setSource(InputDevice.SOURCE_CLASS_POINTER);
                l.onMouseEvent(event);
            }
            else if (!mLastClicked && clicked) {
                MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                        pos.x, pos.y, 0.0f, DEFAULT_SIZE, DEFAULT_META_STATE,
                        DEFAULT_PRECISION_X, DEFAULT_PRECISION_Y, DEFAULT_DEVICE_ID,
                        DEFAULT_EDGE_FLAGS);
                event.setSource(InputDevice.SOURCE_CLASS_POINTER);
                l.onMouseEvent(event);
            }
        }
        catch (RemoteException e) {
            // Just log it and go on
            EVIACAM.debug("RemoteException while sending mouse event");
        }
        mLastPos.set(pos.x, pos.y);
        mLastClicked= clicked;
    }

    /*
     * Process incoming motion
     * 
     * this method is called from a secondary thread 
     */
    @Override
    public void processMotion(PointF motion) {
        // update pointer location given face motion
        mPointerControl.updateMotion(motion);
        
        // get new pointer location
        PointF pointerLocation= mPointerControl.getPointerLocation();
        
        Point pInt= new Point();
        pInt.x= (int) pointerLocation.x;
        pInt.y= (int) pointerLocation.y;
        
        boolean clickGenerated= false;
        if (mAccessibilityAction== null || mAccessibilityAction.isActionable(pInt)) {
            // dwell clicking update
            clickGenerated= mDwellClick.updatePointerLocation(pointerLocation);
        }
        else {
            mDwellClick.reset();
        }
        
        // update pointer position and click progress
        mPointerLayer.updatePosition(pointerLocation);
        if (mAccessibilityAction!= null) {
            mPointerLayer.setRestModeAppearance(mAccessibilityAction.getRestModeEnabled());
        }
        mPointerLayer.updateClickProgress(mDwellClick.getClickProgressPercent());
        mPointerLayer.postInvalidate();
        
        // this needs to be called regularly
        if (mAccessibilityAction!= null) {
            mAccessibilityAction.refresh();
                
            // perform action when needed
            if (clickGenerated) {
                mAccessibilityAction.performAction(pInt);
            }
        }

        checkAndSendEvents(pInt, clickGenerated);
    }
}