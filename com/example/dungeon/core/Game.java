package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Game {
    private final GameState state = new GameState();
    private final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        WorldInfo.touch("Game");
    }

    public Game() {
        registerCommands();
        bootstrapWorld();
    }

    private void registerCommands() {
        commands.put("help", (ctx, a) -> System.out.println("Команды: " + String.join(", ", commands.keySet())));
        commands.put("gc-stats", (ctx, a) -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory(), total = rt.totalMemory(), used = total - free;
            System.out.println("Память: used=" + used + " free=" + free + " total=" + total);
        });
        commands.put("look", (ctx, a) -> System.out.println(ctx.getCurrent().describe()));

        // 1. move <north|south|east|west>
        commands.put("move", (ctx, a) -> {
            if (a.isEmpty()) throw new InvalidCommandException("Укажите направление: north, south, east, west");
            String dir = a.get(0).toLowerCase(Locale.ROOT);
            Room current = ctx.getCurrent();
            Room next = current.getNeighbors().get(dir);
            if (next == null) throw new InvalidCommandException("Нет выхода в направлении: " + dir);
            ctx.setCurrent(next);
            System.out.println("Вы перешли в: " + next.getName());
            System.out.println(next.describe());
        });

        // 2. take <item name>
        commands.put("take", (ctx, a) -> {
            if (a.isEmpty()) throw new InvalidCommandException("Укажите название предмета для взятия");
            String itemName = String.join(" ", a);
            Room room = ctx.getCurrent();
            Optional<Item> optItem = room.getItems().stream()
                    .filter(i -> i.getName().equalsIgnoreCase(itemName))
                    .findFirst();
            if (optItem.isEmpty()) throw new InvalidCommandException("Предмет не найден в комнате: " + itemName);
            Item item = optItem.get();
            room.getItems().remove(item);
            ctx.getPlayer().getInventory().add(item);
            System.out.println("Взято: " + item.getName());
        });

        // 3. inventory
        commands.put("inventory", (ctx, a) -> {
            var inv = ctx.getPlayer().getInventory();
            if (inv.isEmpty()) {
                System.out.println("Инвентарь пуст.");
                return;
            }
            // Группировка по типу предмета + сортировка по имени
            Map<String, List<Item>> grouped = inv.stream()
                    .sorted(Comparator.comparing(Item::getName))
                    .collect(Collectors.groupingBy(i -> i.getClass().getSimpleName(), LinkedHashMap::new, Collectors.toList()));

            grouped.forEach((type, items) -> {
                System.out.printf("- %s (%d): %s%n", type, items.size(),
                        items.stream().map(Item::getName).collect(Collectors.joining(", ")));
            });
        });

        // 4. use <item name>
        commands.put("use", (ctx, a) -> {
            if (a.isEmpty()) throw new InvalidCommandException("Укажите название предмета для использования");
            String itemName = String.join(" ", a);
            Player p = ctx.getPlayer();
            Optional<Item> optItem = p.getInventory().stream()
                    .filter(i -> i.getName().equalsIgnoreCase(itemName))
                    .findFirst();
            if (optItem.isEmpty()) throw new InvalidCommandException("Нет такого предмета в инвентаре: " + itemName);
            Item item = optItem.get();
            item.apply(ctx);
        });

        // 5. fight
        commands.put("fight", (ctx, a) -> {
            Room room = ctx.getCurrent();
            Monster monster = room.getMonster();
            if (monster == null) throw new InvalidCommandException("В комнате нет монстра для боя");
            Player player = ctx.getPlayer();

            System.out.println("Начинается бой с " + monster.getName() + " (ур. " + monster.getLevel() + ")!");

            try {
                // Простой пошаговый бой
                while (player.getHp() > 0 && monster.getHp() > 0) {
                    // Игрок наносит урон
                    int playerAttack = player.getAttack();
                    monster.setHp(monster.getHp() - playerAttack);
                    System.out.printf("Вы бьёте %s на %d HP. Монстр HP: %d%n",
                            monster.getName(), playerAttack, Math.max(monster.getHp(), 0));
                    if (monster.getHp() <= 0) break;

                    // Монстр наносит урон (уровень монстра)
                    int monsterAttack = monster.getLevel();
                    player.setHp(player.getHp() - monsterAttack);
                    System.out.printf("Монстр отвечает на %d. Ваше HP: %d%n",
                            monsterAttack, Math.max(player.getHp(), 0));
                }
            } catch (Exception e) {
                System.out.println("Ошибка в бою: " + e.getMessage());
            }

            if (player.getHp() <= 0) {
                System.out.println("Вы погибли. Игра окончена.");
                System.exit(0);
            }

            if (monster.getHp() <= 0) {
                System.out.println("Монстр повержен!");
                room.setMonster(null);
                // Бросаем лут (например, зелье)
                Potion loot = new Potion("Зелье здоровья", 10);
                room.getItems().add(loot);
                System.out.println("Монстр оставил: " + loot.getName());
            }
        });

        commands.put("save", (ctx, a) -> SaveLoad.save(ctx));
        commands.put("load", (ctx, a) -> SaveLoad.load(ctx));
        commands.put("scores", (ctx, a) -> SaveLoad.printScores());
        commands.put("exit", (ctx, a) -> {
            System.out.println("Пока!");
            System.exit(0);
        });
    }

    private void bootstrapWorld() {
        Player hero = new Player("Герой", 20, 5);
        state.setPlayer(hero);

        Room square = new Room("Площадь", "Каменная площадь с фонтаном.");
        Room forest = new Room("Лес", "Шелест листвы и птичий щебет.");
        Room cave = new Room("Пещера", "Темно и сыро.");
        square.getNeighbors().put("north", forest);
        forest.getNeighbors().put("south", square);
        forest.getNeighbors().put("east", cave);
        cave.getNeighbors().put("west", forest);

        forest.getItems().add(new Potion("Малое зелье", 5));
        forest.setMonster(new Monster("Волк", 1, 8));

        state.setCurrent(square);
    }

    public void run() {
        System.out.println("DungeonMini (TEMPLATE). 'help' — команды.");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> parts = Arrays.asList(line.split("\\s+"));
                String cmd = parts.get(0).toLowerCase(Locale.ROOT);
                List<String> args = parts.subList(1, parts.size());
                Command c = commands.get(cmd);
                try {
                    if (c == null) throw new InvalidCommandException("Неизвестная команда: " + cmd);
                    c.execute(state, args);
                    state.addScore(1);
                } catch (InvalidCommandException e) {
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (ArithmeticException e) {
                    // Пример ошибки выполнения
                    System.out.println("Ошибка выполнения: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Непредвиденная ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода/вывода: " + e.getMessage());
        }
    }

    /*
    Пример ошибки компиляции (раскомментируйте, чтобы увидеть):
    // int x = "string"; // Нельзя присвоить строку в int - ошибка компиляции

    Пример ошибки выполнения:
    // int y = 1 / 0; // ArithmeticException: / by zero
    */
}
