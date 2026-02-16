package com.hypixel.hytale.component.system;

import com.hypixel.hytale.component.SystemGroup;

public interface ISystem<ECS_TYPE> {
    /** Get the system group this system belongs to. */
    default SystemGroup<ECS_TYPE> getGroup() { return null; }
}
