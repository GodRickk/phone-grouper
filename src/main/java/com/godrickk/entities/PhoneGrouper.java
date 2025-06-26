package com.godrickk.entities;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;


public class PhoneGrouper {
    private static final int BUFFER_SIZE = 8192 * 1024;
    private static final int INITIAL_CAPACITY = 50_000;
    private static final int BATCH_SIZE = 500;

    private static class ProcessingContext {
        final List<Set<String>> groups = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Integer> valueToGroupId = new ConcurrentHashMap<>(INITIAL_CAPACITY);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java -jar program.jar <input_file_full_path>");
            return;
        }

        long startTime = System.currentTimeMillis();

        ProcessingContext context = new ProcessingContext();
        int processors = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(processors);
        CompletionService<Void> completionService = new ExecutorCompletionService<>(executor);

        int totalLines = 0;
        int activeTasks = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(args[0]), BUFFER_SIZE)) {
            List<String> batch = new ArrayList<>(BATCH_SIZE);
            String line;

            while ((line = reader.readLine()) != null) {
                totalLines++;
                batch.add(line);

                if (batch.size() >= BATCH_SIZE) {
                    List<String> currentBatch = new ArrayList<>(batch);
                    completionService.submit(() -> {
                        processBatch(currentBatch, context);
                        return null;
                    });

                    activeTasks++;
                    batch.clear();

                    while (activeTasks > processors * 2) {
                        try {
                            completionService.take();
                            activeTasks--;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Прервано во время обработки", e);
                        }
                    }
                }

                if (totalLines % 50000 == 0) {
                    System.gc();
                }
            }

            if (!batch.isEmpty()) {
                List<String> finalBatch = new ArrayList<>(batch);
                completionService.submit(() -> {
                    processBatch(finalBatch, context);
                    return null;
                });
                activeTasks++;
            }

            while (activeTasks > 0) {
                try {
                    completionService.take();
                    activeTasks--;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Прервано во время завершения", e);
                }
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        List<Set<String>> validGroups = new ArrayList<>();
        for (Set<String> group : context.groups) {
            if (group != null && group.size() > 1) {
                validGroups.add(group);
            }
        }

        validGroups.sort((g1, g2) -> Integer.compare(g2.size(), g1.size()));

        System.gc();

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("output.txt")))) {
            writer.println("Количество групп с более чем одним элементом: " + validGroups.size());
            writer.println();

            for (int i = 0; i < validGroups.size(); i++) {
                writer.println("Группа " + (i + 1));
                for (String groupLine : validGroups.get(i)) {
                    writer.println(groupLine);
                }
                writer.println();
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Время выполнения: " + (endTime - startTime) + " мс");
        System.out.println("Обработано строк: " + totalLines);
        System.out.println("Найдено групп: " + validGroups.size());
    }

    private static void processBatch(List<String> batch, ProcessingContext context) {
        for (String line : batch) {
            if (!isValidLine(line)) continue;

            Set<Integer> groupIds = new HashSet<>();
            String[] values = line.split(";");

            for (int i = 0; i < values.length; i++) {
                String value = cleanValue(values[i]);
                if (!value.isEmpty()) {
                    String key = value + ":" + i;
                    Integer groupId = context.valueToGroupId.get(key);
                    if (groupId != null) {
                        groupIds.add(groupId);
                    }
                }
            }

            int targetGroupId;
            synchronized (context.groups) {
                if (groupIds.isEmpty()) {
                    targetGroupId = context.groups.size();
                    context.groups.add(Collections.synchronizedSet(new HashSet<>()));
                } else {
                    targetGroupId = Collections.min(groupIds);

                    if (groupIds.size() > 1) {
                        Set<String> targetGroup = context.groups.get(targetGroupId);
                        for (int groupId : groupIds) {
                            if (groupId != targetGroupId && context.groups.get(groupId) != null) {
                                targetGroup.addAll(context.groups.get(groupId));
                                context.groups.set(groupId, null);
                            }
                        }
                    }
                }

                context.groups.get(targetGroupId).add(line);
            }

            for (int i = 0; i < values.length; i++) {
                String value = cleanValue(values[i]);
                if (!value.isEmpty()) {
                    String key = value + ":" + i;
                    context.valueToGroupId.put(key, targetGroupId);
                }
            }
        }
    }


    private static boolean isValidLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }

        String[] parts = line.split(";");

        for (String part : parts) {
            if (part.isEmpty() || part.equals("\"\"")) {
                continue;
            }

            String cleanPart = part;
            if (part.startsWith("\"") && part.endsWith("\"")) {
                cleanPart = part.substring(1, part.length() - 1);
            }

            if (!cleanPart.matches("^[0-9]+$")) {
                return false;
            }
        }

        return true;
    }

    private static String cleanValue(String value) {
        if (!value.contains("\"")) return value.trim();
        return value.replace("\"", "").trim();
    }
}