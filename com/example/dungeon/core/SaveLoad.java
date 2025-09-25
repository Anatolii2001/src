package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class SaveLoad {
    private static final Path SAVE = Paths.get("save.txt");
    private static final Path SCORES = Paths.get("scores.csv");

    public static void save(GameState s) {
        try (BufferedWriter w = Files.newBufferedWriter(SAVE)) {
            Player p = s.getPlayer();
            w.write("player;" + p.getName() + ";" + p.getHp() + ";" + p.getAttack());
            w.newLine();
            String inv = p.getInventory().stream().map(i -> i.getClass().getSimpleName() + ":" + i.getName()).collect(Collectors.joining(","));
            w.write("inventory;" + inv);
            w.newLine();
            w.write("current;" + s.getCurrent().getName());
            w.newLine();
            w.write("room;" + s.getCurrent().getName());
            w.newLine();
            for (Room room : s.getAllRooms()) {
                String itemsStr = room.getItems().stream()
                        .map(i -> i.getClass().getSimpleName() + ":" + i.getName())
                        .collect(Collectors.joining(","));
                String monsterStr = (room.getMonster() != null)
                        ? room.getMonster().getName() + ";" + room.getMonster().getLevel() + ";" + room.getMonster().getHp()
                        : "";
                String neighborsStr = String.join(",", room.getNeighbors().keySet());
                String lockedStr = room.getLockedExits().entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.joining(","));
                w.write("room;" + room.getName() + ";" + room.getDescription() + ";" + itemsStr + ";" + monsterStr + ";" + neighborsStr + ";" + lockedStr);
                w.newLine();
            }
            System.out.println("Сохранено в " + SAVE.toAbsolutePath());
            writeScore(p.getName(), s.getScore());
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить игру", e);
        }
    }

    public static void load(GameState s) {
        if (!Files.exists(SAVE)) {
            System.out.println("Сохранение не найдено.");
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(SAVE)) {
            Map<String, String> map = new HashMap<>();
            for (String line; (line = r.readLine()) != null; ) {
                String[] parts = line.split(";", 2);
                if (parts.length == 2) map.put(parts[0], parts[1]);
            }
            Player p = s.getPlayer();
            String playerStr = map.getOrDefault("player", "Герой;20;5");
            String[] pp = playerStr.split(";");
            if (pp.length >= 3) {
                p.setName(pp[0]);  // Имя
                p.setHp(Integer.parseInt(pp[1]));  // HP
                p.setAttack(Integer.parseInt(pp[2]));  // Атака
            } else {
                // Fallback на дефолтные значения, если что-то пошло не так
                p.setName("Герой");
                p.setHp(20);
                p.setAttack(5);
            }
            p.getInventory().clear();
            String inv = map.getOrDefault("inventory", "");
            if (!inv.isBlank()) for (String tok : inv.split(",")) {
                String[] t = tok.split(":", 2);
                if (t.length < 2) continue;
                switch (t[0]) {
                    case "Potion" -> p.getInventory().add(new Potion(t[1], 5));
                    case "Key" -> p.getInventory().add(new Key(t[1]));
                    case "Weapon" -> p.getInventory().add(new Weapon(t[1], 3));
                    default -> {
                    }
                }
            }
            System.out.println("Игра загружена (упрощённо).");
            // Новое: разбор комнат
            List<Room> rooms = new ArrayList<>();
            Map<String, Room> roomMap = new HashMap<>();
            Map<String, String> roomData = new HashMap<>();  // Временное хранение для neighbors/locked
            for (String line; (line = r.readLine()) != null; ) {
                String[] parts = line.split(";", 7);  // room;name;desc;items;monster;neighbors;locked
                if (parts.length >= 7 && "room".equals(parts[0])) {
                    String name = parts[1];
                    String desc = parts[2];
                    Room room = new Room(name, desc);
                    // Разбор items
                    if (!parts[3].isBlank()) {
                        for (String tok : parts[3].split(",")) {
                            String[] t = tok.split(":", 2);
                            if (t.length >= 2) {
                                switch (t[0]) {
                                    case "Potion" -> room.getItems().add(new Potion(t[1], 5));
                                    case "Key" -> room.getItems().add(new Key(t[1]));
                                    case "Weapon" -> room.getItems().add(new Weapon(t[1], 3));
                                }
                            }
                        }
                    }
                    // Разбор monster
                    if (!parts[4].isBlank()) {
                        String[] mParts = parts[4].split(";");
                        if (mParts.length >= 3) {
                            room.setMonster(new Monster(mParts[0], Integer.parseInt(mParts[1]), Integer.parseInt(mParts[2])));
                        }
                    }
                    rooms.add(room);
                    roomMap.put(name, room);
                    // Сохраняем neighbors и locked для позднего разбора
                    roomData.put(name + "_neighbors", parts[5]);
                    roomData.put(name + "_locked", parts[6]);
                } else if (parts.length >= 2) {
                    map.put(parts[0], parts[1]);  // Для current и др.
                }
            }
            // После создания всех комнат: восстанавливаем neighbors и locked
            for (Room room : rooms) {
                String neighborsStr = roomData.get(room.getName() + "_neighbors");
                if (neighborsStr != null && !neighborsStr.isBlank()) {
                    for (String dir : neighborsStr.split(",")) {
                        // Простая логика: предполагаем, что соседи симметричны
                        Room neighbor = roomMap.get(room.getName().equals("Площадь") && dir.equals("north") ? "Лес" :
                                room.getName().equals("Лес") && dir.equals("south") ? "Площадь" :
                                        room.getName().equals("Лес") && dir.equals("east") ? "Пещера" :
                                                room.getName().equals("Пещера") && dir.equals("west") ? "Лес" :
                                                        room.getName().equals("Подземелье") && dir.equals("north") ? "Лес" :
                                                                room.getName().equals("Лес") && dir.equals("south") ? "Подземелье" : null);
                        if (neighbor != null) room.getNeighbors().put(dir, neighbor);
                    }
                }
                String lockedStr = roomData.get(room.getName() + "_locked");
                if (lockedStr != null && !lockedStr.isBlank()) {
                    for (String dir : lockedStr.split(",")) {
                        room.getLockedExits().put(dir, true);
                    }
                }
            }
            s.setAllRooms(rooms);
            // Устанавливаем текущую комнату
            String currentName = map.getOrDefault("current", "Площадь");
            s.setCurrent(roomMap.get(currentName));
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось загрузить игру", e);
        }
    }

    public static void printScores() {
        if (!Files.exists(SCORES)) {
            System.out.println("Пока нет результатов.");
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(SCORES)) {
            System.out.println("Таблица лидеров (топ-10):");
            r.lines().skip(1).map(l -> l.split(",")).map(a -> new Score(a[1], Integer.parseInt(a[2])))
                    .sorted(Comparator.comparingInt(Score::score).reversed()).limit(10)
                    .forEach(s -> System.out.println(s.player() + " — " + s.score()));
        } catch (IOException e) {
            System.err.println("Ошибка чтения результатов: " + e.getMessage());
        }
    }

    private static void writeScore(String player, int score) {
        try {
            boolean header = !Files.exists(SCORES);
            try (BufferedWriter w = Files.newBufferedWriter(SCORES, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (header) {
                    w.write("ts,player,score");
                    w.newLine();
                }
                w.write(LocalDateTime.now() + "," + player + "," + score);
                w.newLine();
            }
        } catch (IOException e) {
            System.err.println("Не удалось записать очки: " + e.getMessage());
        }
    }

    private record Score(String player, int score) {
    }
}
