package com.zerostudio.decompiler.api;

public interface Decompiler {
    String name();
    String version();
    DecompileResult decompile(DecompileRequest request);
}