package ru.bogempty.client.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    VISUAL("Visual"),
    HUD("HUD"),
    AI("Ai");

    public final String name;

    Category(String name) {
        this.name = name;
    }
}