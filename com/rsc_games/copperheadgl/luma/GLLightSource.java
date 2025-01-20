package com.rsc_games.copperheadgl.luma;

import java.awt.Color;

import velocity.Rect;
import velocity.renderer.InternalLightSource;

import com.rsc_games.copperheadgl.GLLightingEngine;

public abstract class GLLightSource extends InternalLightSource {
    protected GLLightingEngine le;

    public GLLightSource(GLLightingEngine le, float intensity, Color c) {
        this.le = le;
        this.intensity = intensity;
        this.lightid = le.registerLightSource(this);
    }

    /**
     * Set this light's intensity.
     */
    @Override
    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public abstract boolean canCull(Rect other);

    public abstract Rect getRect();
}
