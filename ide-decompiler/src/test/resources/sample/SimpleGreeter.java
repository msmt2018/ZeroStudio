/*
 * A simple Java class that the CFR test will compile to .class and
 * then decompile back. We use the result of decompilation to verify
 * the engine's round-trip is sensible.
 */
package com.example.decompile;

public class SimpleGreeter {
    private final String greeting;

    public SimpleGreeter(String greeting) {
        this.greeting = greeting;
    }

    public String greet(String name) {
        return greeting + ", " + name + "!";
    }

    public int count() {
        return 42;
    }
}
