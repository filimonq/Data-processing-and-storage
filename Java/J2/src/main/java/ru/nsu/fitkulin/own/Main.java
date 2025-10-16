package ru.nsu.fitkulin.own;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
    private static List<String> chunk80(String s) {
        List<String> res = new ArrayList<>();
        for (int i = 0; i < s.length(); i += 80)
            res.add(s.substring(i, Math.min(i + 80, s.length())));
        return res;
    }

    public static void main(String[] args) throws Exception {
        int threads = args.length >= 1 ? Integer.parseInt(args[0]) : 2;
        long betweenMs = args.length >= 2 ? Long.parseLong(args[1]) : 100;
        long insideMs  = args.length >= 3 ? Long.parseLong(args[2]) : 100;

        System.out.printf("LinkedList mode | sorters=%d | delays: between=%dms, inside=%dms%n",
                threads, betweenMs, insideMs);

        LockingSinglyLinkedList list = new LockingSinglyLinkedList();
        AtomicLong steps = new AtomicLong();

        var runners = new ArrayList<BubbleSorter>();
        var workerThreads = new ArrayList<Thread>();
        for (int i = 0; i < threads; i++) {
            BubbleSorter r = new BubbleSorter(list, betweenMs, insideMs, steps);
            Thread t = new Thread(r, "sorter-" + i);
            runners.add(r);
            workerThreads.add(t);
            t.start();
        }

        System.out.println("Введите строки. Пустая строка — печать. EOF — выход.");
        try (var br = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    System.out.println("---- Список (size=" + list.size() + ", steps=" + steps.get() + ") ----");
                    for (String s : list) System.out.println(s); // for-each, как требуют
                    System.out.println("-------------------------------------");
                } else {
                    var parts = chunk80(line);
                    for (int i = parts.size() - 1; i >= 0; i--) list.pushFront(parts.get(i));
                }
            }
        } finally {
            for (BubbleSorter r : runners) r.stop();
            for (Thread t : workerThreads) t.interrupt();
            for (Thread t : workerThreads) t.join();
        }
    }
}
