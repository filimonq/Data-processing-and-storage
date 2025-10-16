package ru.nsu.fitkulin.own;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

final class LockingSinglyLinkedList implements Iterable<String> {
    private final Node head = new Node(null);
    private final AtomicInteger size = new AtomicInteger(0);

    public int size() { return size.get(); }

    public void pushFront(String s) {
        Node n = new Node(s);
        head.lock.lock();
        try {
            Node oldFirst = head.next;
            if (oldFirst != null) oldFirst.lock.lock();
            try {
                n.next = oldFirst;
                head.next = n;
                size.incrementAndGet();
            } finally {
                if (oldFirst != null) oldFirst.lock.unlock();
            }
        } finally {
            head.lock.unlock();
        }
    }

    public int bubblePass(long insideDelayMs, long betweenDelayMs, Runnable stepHook) throws InterruptedException {
        int swaps = 0;
        Node prev = head;

        while (true) {
            Node nextPrev = null; // сюда положим, кем станет prev после освобождения замков

            // 1) Блокируем prev
            prev.lock.lock();
            Node a;
            try {
                a = prev.next;
                if (a == null) {            // список короче двух элементов
                    return swaps;
                }

                // 2) Блокируем a
                a.lock.lock();
                Node b;
                try {
                    // проверка смежности (вдруг другая нить что-то передвинула)
                    if (prev.next != a) {
                        nextPrev = prev;    // просто повторим с тем же prev
                    } else {
                        b = a.next;
                        if (b == null) {    // достигли хвоста
                            return swaps;
                        }

                        // 3) Блокируем b
                        b.lock.lock();
                        try {
                            if (a.next != b) {
                                nextPrev = prev; // нарушилась смежность — повторим
                            } else {
                                // --- внутри шага (под замками пары) ---
                                if (insideDelayMs > 0) Thread.sleep(insideDelayMs);
                                if (stepHook != null) stepHook.run(); // считаем ПОПЫТКУ

                                if (a.value.compareTo(b.value) > 0) {
                                    // перестановка ссылок: prev -> b -> a -> ...
                                    Node afterB = b.next;
                                    a.next = afterB;
                                    b.next = a;
                                    prev.next = b;
                                    swaps++;
                                    nextPrev = prev.next; // т.е. b — продвинулись на один узел
                                } else {
                                    nextPrev = a;         // без свопа prev сдвигается на a
                                }
                            }
                        } finally {
                            b.lock.unlock();
                        }
                    }
                } finally {
                    a.lock.unlock();
                }
            } finally {
                prev.lock.unlock();
            }

            // --- задержка между шагами (вне замков) ---
            if (betweenDelayMs > 0) Thread.sleep(betweenDelayMs);

            // обновляем prev ТОЛЬКО после того, как все замки освобождены
            prev = (nextPrev != null) ? nextPrev : head;
        }
    }


    // Итератор для for-each: «живой» просмотр с поузловой блокировкой (head->curr->next).
    @Override public Iterator<String> iterator() {
        return new Iterator<String>() {
            Node curr;

            {
                head.lock.lock();
                try {
                    curr = head.next;
                    if (curr != null) curr.lock.lock();
                } finally {
                    head.lock.unlock();
                }
            }

            @Override public boolean hasNext() { return curr != null; }

            @Override public String next() {
                if (curr == null) throw new NoSuchElementException();
                String out = curr.value;

                // lock next, then unlock curr (порядок слева направо сохраняется)
                Node nxt = curr.next;
                if (nxt != null) nxt.lock.lock();
                curr.lock.unlock();
                curr = nxt;

                return out;
            }
        };
    }
}
