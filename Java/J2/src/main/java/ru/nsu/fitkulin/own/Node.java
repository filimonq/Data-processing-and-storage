package ru.nsu.fitkulin.own;

import java.util.concurrent.locks.ReentrantLock;

final class Node {
    final ReentrantLock lock = new ReentrantLock();
    String value;
    Node next;
    Node(String v) { this.value = v; }
}
