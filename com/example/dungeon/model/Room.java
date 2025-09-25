package com.example.dungeon.model;

import java.util.*;

public class Room {
    private final String name;
    private final String description;
    private final Map<String, Room> neighbors = new HashMap<>();
    private final List<Item> items = new ArrayList<>();
    private Monster monster;
    private final Map<String, Boolean> lockedExits = new HashMap<>();

    public Room(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Room> getNeighbors() {
        return neighbors;
    }

    public List<Item> getItems() {
        return items;
    }

    public Monster getMonster() {
        return monster;
    }

    public void setMonster(Monster m) {
        this.monster = m;
    }

    public Map<String, Boolean> getLockedExits() {
        return lockedExits;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder(name + ": " + description);
        if (!items.isEmpty()) {
            sb.append("\nПредметы: ").append(String.join(", ", items.stream().map(Item::getName).toList()));
        }
        if (monster != null) {
            sb.append("\nВ комнате монстр: ").append(monster.getName()).append(" (ур. ").append(monster.getLevel()).append(")");
        }
        if (!neighbors.isEmpty()) {
            sb.append("\nВыходы: ").append(String.join(", ", neighbors.keySet()));
            //Показать заблокированные выходы
            List<String> locked = lockedExits.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .toList();
            if (!locked.isEmpty()) {
                sb.append(" (заблокировано: ").append(String.join(", ",locked)).append(")");
            }
        }
        return sb.toString();
    }
}
