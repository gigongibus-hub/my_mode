package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

// Психологічні стани (етап 9) — наповнюється далі
public class CowPsyche {
    private final HungryCow cow;

    public CowPsyche(HungryCow cow) {
        this.cow = cow;
    }

    public void onEpisode(CowEpisodes.Episode e) {
    }

    public void secondTick() {
    }

    public List<String> lines() {
        return new ArrayList<>();
    }

    public void save(CompoundTag tag) {
    }

    public void load(CompoundTag tag) {
    }
}
