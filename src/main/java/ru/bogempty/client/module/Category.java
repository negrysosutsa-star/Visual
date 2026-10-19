package ru.bogempty.client.module;

public enum Category {
    MOVEMENT("Movement"),
    VISUAL("Visual"),
    HUD("HUD"),
    CLIENT("Client");

    public final String name;

    Category(String name) {
        this.name = name;
    }
}