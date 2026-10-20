package org.slf4j;
/** Minimal stand-in so the real BoneRagdollPhysics class loads without the game. */
public interface Logger { void info(String s, Object... o); }
