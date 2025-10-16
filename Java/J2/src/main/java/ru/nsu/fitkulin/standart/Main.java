package ru.nsu.fitkulin.standart;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
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

        System.out.printf("ArrayList mode | sorters=%d | delays: between=%dms, inside=%dms%n",
                threads, betweenMs, insideMs);

        List<String> base = new ArrayList<>();
        List<String> list = Collections.synchronizedList(base);
        AtomicLong steps = new AtomicLong();

        var runners = new ArrayList<ArrayBubbleSorter>();
        var workerThreads = new ArrayList<Thread>();
        for (int i = 0; i < threads; i++) {
            ArrayBubbleSorter r = new ArrayBubbleSorter(list, betweenMs, insideMs, steps);
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
                    System.out.println("---- Список (size=" + base.size() + ", steps=" + steps.get() + ") ----");
                    synchronized (list) { for (String s : list) System.out.println(s); }
                    System.out.println("-------------------------------------");
                } else {
                    var parts = chunk80(line);
                    synchronized (list) {
                        for (int i = parts.size() - 1; i >= 0; i--) list.add(0, parts.get(i));
                    }
                }
            }
        } finally {
            for (ArrayBubbleSorter r : runners) r.stop();
            for (Thread t : workerThreads) t.interrupt();
            for (Thread t : workerThreads) t.join();
        }
    }
}
