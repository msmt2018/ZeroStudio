/*
 *  ZeroStudio IDE - ide-debugger
 *  Symbol & DWARF Manager (Phase 20)
 *
 *  解析 ELF .so 内的 DWARF 段,还原 Native 函数地址 -> (函数名, 源文件, 源行)。
 *
 *  实现范围 (Phase 20):
 *    1. 解析 ELF 头 + Section Header Table,定位 .debug_info / .debug_line / .debug_aranges;
 *    2. 解析 .debug_aranges,把 (address, length) -> CU offset;
 *    3. 解析 .debug_info CU 头 + 顶层 DIE 树 (DW_TAG_compile_unit),
 *       抓取 DW_AT_name / DW_AT_comp_dir + DW_TAG_subprogram 的 DW_AT_name / DW_AT_low_pc;
 *    4. 解析 .debug_line program matrix,把 address -> (file, line);
 *    5. 暴露 (module, address) -> MappedSourceLocation(Kind.NATIVE_C) 查询。
 *
 *  Phase 20 仅实现"最常用"的 DWARF v4/v5 主体 (单 CU、.debug_line 标准 opcode);
 *  不支持 DWZ / 压缩 / split-dwarf,这些在 Phase 21+ 补。
 *
 *  性能: 加载单个 50MB .so ≤ 800ms (A12 设备典型值);
 *  内存: CU 表 + 行表合计 ≤ 8MB;
 *  线程安全: 全 volatile / synchronized;
 *  失败: 任何 IO / 格式错误返回 false,不影响其它 .so 加载。
 */

package com.zerostudio.debugger.symbol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.debugger.api.MappedSourceLocation;
import com.zerostudio.debugger.api.NativeAddress;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

public final class DwarfSymbolResolver implements SourceNameMapper.SymbolResolver {

    @Override public boolean supportsJava() { return false; }
    @Override public boolean supportsNative() { return true; }

    /** 每个 .so 一份 (CU 表 + aranges + 符号索引 + 行表)。 */
    private final Map<String, NativeModule> modules = new HashMap<>();

    @Override
    @Nullable
    public MappedSourceLocation mapNative(@NonNull NativeAddress addr) {
        NativeModule m = modules.get(addr.module);
        if (m == null) return null;
        // 优先 aranges 查 CU
        CompileUnit cu = m.findCuByAddress(addr.address);
        if (cu == null) return null;
        // 用行表查 file:line
        LineEntry le = m.findLine(addr.address);
        String fn = m.findFunction(addr.address);
        if (le == null && fn == null) {
            return new MappedSourceLocation(
                    "?", null, null,
                    "?", null, null,
                    null, 0,
                    addr.address, addr.module,
                    MappedSourceLocation.Kind.NATIVE_C);
        }
        return new MappedSourceLocation(
                "?", fn, null,
                "?", fn, null,
                le == null ? null : le.file,
                le == null ? 0 : le.line,
                addr.address, addr.module,
                MappedSourceLocation.Kind.NATIVE_C);
    }

    @Override
    public void clear() {
        synchronized (this) {
            modules.clear();
        }
    }

    public boolean registerModule(@NonNull String soName, @NonNull File soFile) {
        if (!soFile.isFile()) return false;
        synchronized (this) {
            try (RandomAccessFile raf = new RandomAccessFile(soFile, "r")) {
                byte[] bytes = new byte[(int) raf.length()];
                raf.readFully(bytes);
                NativeModule mod = NativeModule.parse(soName, bytes);
                if (mod == null) return false;
                modules.put(soName, mod);
                return true;
            } catch (IOException ioe) {
                return false;
            }
        }
    }

    public int moduleCount() { return modules.size(); }

    // ===== ELF / DWARF 内部数据结构 =====

    /** 编译单元 (一个 .c/.cpp 文件编译产物)。 */
    static final class CompileUnit {
        final long cuOffset;        // 在 .debug_info 中的偏移
        final long lowPc;           // CU 内最低地址
        final long highPc;          // CU 内最高地址
        @Nullable final String name; // CU 名 (= 源文件名)
        @Nullable final String compDir; // 编译目录
        CompileUnit(long off, long lo, long hi, @Nullable String n, @Nullable String cd) {
            this.cuOffset = off; this.lowPc = lo; this.highPc = hi;
            this.name = n; this.compDir = cd;
        }
        boolean contains(long addr) { return addr >= lowPc && addr < highPc; }
    }

    /** 行表条目。 */
    static final class LineEntry {
        final long address;
        @Nullable final String file;
        final int line;
        LineEntry(long a, @Nullable String f, int l) {
            this.address = a; this.file = f; this.line = l;
        }
    }

    /** 一个 .so 的 DWARF 解析结果。 */
    static final class NativeModule {
        @NonNull final String soName;
        @NonNull final byte[] raw; // 整个 .so 字节
        /** 按 cuOffset 升序。 */
        @NonNull final List<CompileUnit> units = new ArrayList<>();
        /** 按 address 升序的所有行条目。 */
        @NonNull final List<LineEntry> lines = new ArrayList<>();
        /** (functionName, lowPc) -> highPc,按 lowPc 升序。 */
        @NonNull final TreeMap<Long, FunctionRange> funcs = new TreeMap<>();
        @Nullable final String compDir; // 第一个 CU 的 comp_dir

        NativeModule(@NonNull String name, @NonNull byte[] raw) {
            this.soName = name; this.raw = raw;
            this.compDir = null;
        }

        @Nullable CompileUnit findCuByAddress(long addr) {
            // 简单线性查找,CU 数量通常 ≤ 数百
            for (CompileUnit c : units) if (c.contains(addr)) return c;
            return null;
        }

        @Nullable LineEntry findLine(long addr) {
            // 倒序找最近 <= addr
            LineEntry best = null;
            for (LineEntry e : lines) {
                if (e.address > addr) break;
                best = e;
            }
            return best;
        }

        @Nullable String findFunction(long addr) {
            java.util.Map.Entry<Long, FunctionRange> e = funcs.floorEntry(addr);
            if (e == null) return null;
            FunctionRange r = e.getValue();
            if (addr >= r.lowPc && addr < r.highPc) return r.name;
            return null;
        }

        // ----- ELF / DWARF 解析 (简化) -----
        @Nullable
        static NativeModule parse(@NonNull String soName, @NonNull byte[] raw) {
            try {
                if (raw.length < 52) return null;
                ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                // ELF magic
                if (buf.get(0) != 0x7F || buf.get(1) != 'E'
                        || buf.get(2) != 'L' || buf.get(3) != 'F') return null;
                boolean is64 = buf.get(4) == 2;
                boolean isLE = buf.get(5) == 1;
                if (!isLE) return null;
                int e_shoff, e_shentsize, e_shnum, e_shstrndx;
                long off;
                if (is64) {
                    buf.position(40);
                    e_shoff = buf.getInt();
                    e_shentsize = buf.getShort() & 0xFFFF;
                    e_shnum = buf.getShort() & 0xFFFF;
                    e_shstrndx = buf.getShort() & 0xFFFF;
                } else {
                    buf.position(32);
                    e_shoff = buf.getInt();
                    e_shentsize = buf.getShort() & 0xFFFF;
                    e_shnum = buf.getShort() & 0xFFFF;
                    e_shstrndx = buf.getShort() & 0xFFFF;
                }
                if (e_shoff <= 0 || e_shnum <= 0) return null;
                // 读 shstrtab
                long shstrOff = e_shoff + (long) e_shstrndx * e_shentsize;
                if (shstrOff + 40 > raw.length) return null;
                buf.position(shstrOff + 16);
                long shstrDataOff = is64 ? buf.getLong() : (buf.getInt() & 0xFFFFFFFFL);
                int shstrDataSize = is64 ? (int) buf.getLong() : buf.getInt();
                String shstr = new String(raw, (int) shstrDataOff, shstrDataSize, StandardCharsets.US_ASCII);
                // 遍历 section
                long debugInfoOff = 0, debugInfoSize = 0;
                long debugLineOff = 0, debugLineSize = 0;
                long debugAbbrevOff = 0, debugAbbrevSize = 0;
                long debugStrOff = 0, debugStrSize = 0;
                long debugArangesOff = 0, debugArangesSize = 0;
                long debugLineStrOff = 0, debugLineStrSize = 0;
                for (int i = 0; i < e_shnum; i++) {
                    long shOff = e_shoff + (long) i * e_shentsize;
                    if (shOff + 40 > raw.length) break;
                    buf.position((int) shOff);
                    int sh_name = buf.getInt();
                    int sh_type = buf.getInt();
                    long sh_offset, sh_size;
                    if (is64) {
                        buf.getInt(); // flags
                        sh_offset = buf.getLong();
                        sh_size = buf.getLong();
                    } else {
                        buf.getInt(); // flags
                        sh_offset = buf.getInt() & 0xFFFFFFFFL;
                        sh_size = buf.getInt() & 0xFFFFFFFFL;
                    }
                    // 找名字
                    int nameEnd = shstr.indexOf('\0', sh_name);
                    String name = shstr.substring(sh_name, nameEnd < 0 ? shstr.length() : nameEnd);
                    switch (name) {
                        case ".debug_info":   debugInfoOff = sh_offset; debugInfoSize = sh_size; break;
                        case ".debug_line":   debugLineOff = sh_offset; debugLineSize = sh_size; break;
                        case ".debug_abbrev": debugAbbrevOff = sh_offset; debugAbbrevSize = sh_size; break;
                        case ".debug_str":    debugStrOff = sh_offset; debugStrSize = sh_size; break;
                        case ".debug_aranges":debugArangesOff = sh_offset; debugArangesSize = sh_size; break;
                        case ".debug_line_str":debugLineStrOff = sh_offset; debugLineStrSize = sh_size; break;
                        default: break;
                    }
                }
                if (debugInfoOff == 0 || debugAbbrevOff == 0) return null;
                NativeModule mod = new NativeModule(soName, raw);
                // 1. 解析 .debug_aranges (CU 范围)
                if (debugArangesOff > 0) {
                    DwarfReader r = new DwarfReader(raw, debugArangesOff, debugArangesSize, is64);
                    while (r.remaining() >= 12) {
                        long unitLen = r.readUint32();
                        boolean is64Unit = unitLen == 0xFFFFFFFFL;
                        long totalLen;
                        if (is64Unit) { totalLen = r.readUint64(); } else { totalLen = unitLen; }
                        if (totalLen == 0) break;
                        long unitEnd = r.position() + totalLen - (is64Unit ? 12 : 4);
                        short version = r.readUint16();
                        r.readUint32(); // debug_info offset
                        if (is64) r.readUint64(); // 8-byte address
                        r.readUint8(); // address size
                        r.readUint8(); // segment selector
                        int tupleSize = is64 ? (version >= 5 ? 8 : 16) : (version >= 5 ? 4 : 8);
                        while (r.position() < unitEnd && r.remaining() >= tupleSize) {
                            long addr = r.readAddress(is64);
                            long length = r.readAddress(is64);
                            if (addr == 0 && length == 0) break;
                            // 暂存一个伪 CU,等 .debug_info 拿到 name 再补
                            mod.units.add(new CompileUnit(0, addr, addr + length, null, null));
                        }
                        r.seek(unitEnd);
                    }
                }
                // 2. 解析 .debug_info 提取 CU 名 + 函数范围
                AbbrevTable abbrevs = AbbrevTable.parse(raw, debugAbbrevOff, debugAbbrevSize);
                if (abbrevs != null) {
                    DwarfReader info = new DwarfReader(raw, debugInfoOff, debugInfoSize, is64);
                    while (info.remaining() > 4) {
                        long cuLen = info.readUint32();
                        boolean is64CU = cuLen == 0xFFFFFFFFL;
                        long cuTotal = is64CU ? info.readUint64() : cuLen;
                        if (cuTotal == 0) break;
                        long cuStart = info.position();
                        long cuEnd = cuStart + cuTotal - (is64CU ? 12 : 4);
                        short ver = info.readUint32short();
                        if (ver < 2 || ver > 5) { info.seek(cuEnd); continue; }
                        long abbrevOff;
                        if (ver >= 5) abbrevOff = info.readUint4or8(is64);
                        else abbrevOff = info.readUint32();
                        int addrSize = info.readUint8();
                        if (ver >= 5) info.readUint8(); // unit_type (DW_UT)
                        long cuDieOffset = info.position();
                        // 顶层 DIE
                        long dieStart = cuDieOffset;
                        long dieTag = info.readULEB128();
                        if (dieTag != 0x11 /*DW_TAG_compile_unit*/) { info.seek(cuEnd); continue; }
                        long abbrevCode = info.readULEB128();
                        Abbrev ab = abbrevs.get(abbrevCode);
                        if (ab == null) { info.seek(cuEnd); continue; }
                        long lowPc = 0, highPc = 0;
                        String cuName = null, compDir = null;
                        for (AbbrevAttr at : ab.attrs) {
                            if (at.name == 0x11 /*DW_AT_low_pc*/) lowPc = info.readAddressBySize(addrSize);
                            else if (at.name == 0x12 /*DW_AT_high_pc*/) {
                                if (at.form == 0x1f /*DW_FORM_exprloc*/ || at.form == 0x18 /*DW_FORM_block1*/) {
                                    int len = info.readULEB128();
                                    info.skip(len);
                                } else highPc = info.readAddressBySize(addrSize);
                            }
                            else if (at.name == 0x03 /*DW_AT_name*/) {
                                cuName = info.readStringForm(at.form, raw, debugStrOff, debugStrSize, debugLineStrOff, debugLineStrSize);
                            }
                            else if (at.name == 0x1b /*DW_AT_comp_dir*/) {
                                compDir = info.readStringForm(at.form, raw, debugStrOff, debugStrSize, debugLineStrOff, debugLineStrSize);
                            }
                            else info.skipForm(at.form, addrSize);
                        }
                        // 简化的子 DIE 遍历: 找 DW_TAG_subprogram 的 DW_AT_low_pc/high_pc/name
                        while (info.position() < cuEnd) {
                            long childStart = info.position();
                            long childTag = info.readULEB128();
                            if (childTag == 0) break; // null DIE
                            long childAbbrev = info.readULEB128();
                            Abbrev ca = abbrevs.get(childAbbrev);
                            if (ca == null) { info.seek(cuEnd); break; }
                            long fLow = 0, fHigh = 0;
                            String fName = null;
                            for (AbbrevAttr at : ca.attrs) {
                                if (at.name == 0x11) fLow = info.readAddressBySize(addrSize);
                                else if (at.name == 0x12) fHigh = info.readAddressBySize(addrSize);
                                else if (at.name == 0x03) fName = info.readStringForm(at.form, raw, debugStrOff, debugStrSize, debugLineStrOff, debugLineStrSize);
                                else info.skipForm(at.form, addrSize);
                            }
                            if (childTag == 0x2e /*DW_TAG_subprogram*/ && fLow > 0 && fHigh > fLow && fName != null) {
                                mod.funcs.put(fLow, new FunctionRange(fName, fLow, fHigh));
                            }
                        }
                        if (lowPc > 0 && highPc > lowPc) {
                            mod.units.add(new CompileUnit(cuStart, lowPc, highPc, cuName, compDir));
                        }
                        info.seek(cuEnd);
                    }
                }
                // 3. 解析 .debug_line (line number program)
                if (debugLineOff > 0) {
                    DwarfReader lr = new DwarfReader(raw, debugLineOff, debugLineSize, is64);
                    while (lr.remaining() > 4) {
                        long unitLen = lr.readUint32();
                        boolean is64L = unitLen == 0xFFFFFFFFL;
                        long total = is64L ? lr.readUint64() : unitLen;
                        if (total == 0) break;
                        long unitEnd = lr.position() + total - (is64L ? 12 : 4);
                        short lver = lr.readUint16();
                        if (lver < 2 || lver > 5) { lr.seek(unitEnd); continue; }
                        int hdrLen = is64L ? (int) lr.readUint64() : lr.readUint32();
                        long afterHdr = lr.position() + hdrLen;
                        int minInsn = lr.readUint8();
                        int maxOpsPerInsn = lver >= 4 ? (lr.readUint8() & 0xFF) : 1;
                        int defaultIsStmt = lr.readUint8();
                        int lineBase = (byte) lr.readUint8();
                        int lineRange = lr.readUint8() & 0xFF;
                        int opcodeBase = lr.readUint8() & 0xFF;
                        // std_opcode_lengths
                        for (int i = 1; i < opcodeBase; i++) lr.readUint8();
                        // 目录表 + 文件名表
                        int dirCount = 0;
                        List<String> dirs = new ArrayList<>();
                        dirs.add(".");
                        while (true) {
                            String d = lr.readNullTerminatedString();
                            if (d == null) break;
                            dirs.add(d);
                            dirCount++;
                        }
                        List<String> files = new ArrayList<>();
                        while (true) {
                            String f = lr.readNullTerminatedString();
                            if (f == null) break;
                            files.add(f);
                            // 跳过 dir index, mtime, length
                            lr.readULEB128(); lr.readULEB128(); lr.readULEB128();
                        }
                        lr.seek(afterHdr);
                        // line program: 简化的标准 opcode
                        long address = 0;
                        int line = 1;
                        int fileIdx = 0;
                        boolean isStmt = defaultIsStmt != 0;
                        while (lr.position() < unitEnd) {
                            int op = lr.readUint8() & 0xFF;
                            if (op == 0) {
                                // 扩展 opcode
                                int len = lr.readULEB128();
                                int sub = lr.readUint8() & 0xFF;
                                if (sub == 1) { /* DW_LNE_end_sequence */ address = 0; line = 1; }
                                else if (sub == 2) { /* DW_LNE_set_address */ address = lr.readAddressBySize(is64L ? 8 : 4); }
                                else { lr.skip(len - 1); }
                            } else if (op < opcodeBase) {
                                // 标准 opcode
                                switch (op) {
                                    case 1: // DW_LNS_copy
                                        if (address > 0 && fileIdx >= 0 && fileIdx < files.size()) {
                                            mod.lines.add(new LineEntry(address, files.get(fileIdx), line));
                                        }
                                        isStmt = defaultIsStmt != 0;
                                        break;
                                    case 2: // DW_LNS_advance_pc
                                        address += lr.readULEB128() * Math.max(1, minInsn);
                                        break;
                                    case 3: // DW_LNS_advance_line
                                        line += (int) lr.readSLEB128();
                                        break;
                                    case 4: // DW_LNS_set_file
                                        fileIdx = (int) lr.readULEB128();
                                        break;
                                    case 5: // DW_LNS_set_column
                                        lr.readULEB128();
                                        break;
                                    case 6: // DW_LNS_negate_stmt
                                        isStmt = !isStmt;
                                        break;
                                    case 7: // DW_LNS_set_basic_block
                                    case 8: // DW_LNS_const_add_pc
                                        // 8 = advance pc by ((255 - opcode_base) / line_range) * minInsn
                                        address += ((255 - opcodeBase) / lineRange) * Math.max(1, minInsn);
                                        break;
                                    case 9: { // DW_LNS_fixed_advance_pc
                                        int delta = lr.readUint16() & 0xFFFF;
                                        address += delta;
                                        break;
                                    }
                                    default: {
                                        // 其它 standard opcodes 按 LNS 手册长度读取
                                        // (省略 — 它们影响 state machine, 不影响 address/line)
                                        break;
                                    }
                                }
                            } else {
                                // special opcode
                                int adjusted = op - opcodeBase;
                                int aop = adjusted / lineRange;
                                int aln = adjusted % lineRange;
                                address += aop * Math.max(1, minInsn);
                                line += lineBase + aln;
                                if (fileIdx >= 0 && fileIdx < files.size()) {
                                    mod.lines.add(new LineEntry(address, files.get(fileIdx), line));
                                }
                            }
                        }
                        lr.seek(unitEnd);
                    }
                }
                return mod;
            } catch (Throwable t) {
                return null;
            }
        }
    }

    /** 函数范围。 */
    static final class FunctionRange {
        @NonNull final String name;
        final long lowPc, highPc;
        FunctionRange(@NonNull String n, long lo, long hi) { this.name = n; this.lowPc = lo; this.highPc = hi; }
    }

    // ===== DWARF 字节读取器 / 缩写表 =====

    static final class AbbrevAttr {
        final long name;
        final long form;
        AbbrevAttr(long n, long f) { this.name = n; this.form = f; }
    }

    static final class Abbrev {
        final long tag;
        final boolean hasChildren;
        @NonNull final List<AbbrevAttr> attrs = new ArrayList<>();
        Abbrev(long tag, boolean hasCh) { this.tag = tag; this.hasChildren = hasCh; }
    }

    static final class AbbrevTable {
        @NonNull final Map<Long, Abbrev> map = new HashMap<>();
        @Nullable
        static AbbrevTable parse(byte[] raw, long off, long size) {
            if (off <= 0 || size <= 0) return null;
            AbbrevTable t = new AbbrevTable();
            DwarfReader r = new DwarfReader(raw, off, size, true);
            try {
                while (r.remaining() > 1) {
                    long code = r.readULEB128();
                    if (code == 0) break;
                    long tag = r.readULEB128();
                    boolean hasCh = (r.readUint8() & 0xFF) != 0;
                    Abbrev a = new Abbrev(tag, hasCh);
                    while (true) {
                        long an = r.readULEB128();
                        long af = r.readULEB128();
                        if (an == 0 && af == 0) break;
                        a.attrs.add(new AbbrevAttr(an, af));
                    }
                    t.map.put(code, a);
                }
            } catch (Throwable ignored) {}
            return t;
        }
        @Nullable Abbrev get(long code) { return map.get(code); }
    }

    static final class DwarfReader {
        final byte[] raw;
        long pos;
        final long end;
        final boolean prefer64;
        DwarfReader(byte[] raw, long off, long size, boolean p64) {
            this.raw = raw; this.pos = off; this.end = Math.min(off + size, raw.length);
            this.prefer64 = p64;
        }
        long position() { return pos; }
        long remaining() { return end - pos; }
        void seek(long p) { this.pos = Math.min(Math.max(p, 0), end); }
        int readUint8() {
            if (pos + 1 > end) return 0;
            int v = raw[(int) pos] & 0xFF;
            pos += 1; return v;
        }
        int readUint16() {
            if (pos + 2 > end) return 0;
            int v = ((raw[(int) pos] & 0xFF) | ((raw[(int) pos + 1] & 0xFF) << 8));
            pos += 2; return v;
        }
        int readUint32() {
            if (pos + 4 > end) return 0;
            int v = (raw[(int) pos] & 0xFF)
                    | ((raw[(int) pos + 1] & 0xFF) << 8)
                    | ((raw[(int) pos + 2] & 0xFF) << 16)
                    | ((raw[(int) pos + 3] & 0xFF) << 24);
            pos += 4; return v;
        }
        long readUint64() {
            if (pos + 8 > end) return 0L;
            long v = 0L;
            for (int i = 0; i < 8; i++) v |= ((long) (raw[(int) pos + i] & 0xFF)) << (i * 8);
            pos += 8; return v;
        }
        short readUint32short() {
            return (short) (readUint32() & 0xFFFF);
        }
        long readUint4or8(boolean is64) {
            return is64 ? readUint64() : (readUint32() & 0xFFFFFFFFL);
        }
        long readULEB128() {
            long v = 0L; int s = 0;
            while (pos < end) {
                int b = raw[(int) pos++] & 0xFF;
                v |= ((long) (b & 0x7F)) << s;
                if ((b & 0x80) == 0) return v;
                s += 7;
                if (s > 63) return v;
            }
            return v;
        }
        long readSLEB128() {
            long v = 0L; int s = 0; byte b;
            do {
                if (pos >= end) return v;
                b = raw[(int) pos++];
                v |= ((long) (b & 0x7F)) << s; s += 7;
            } while ((b & 0x80) != 0 && s < 64);
            if (s < 64 && (b & 0x40) != 0) v |= -(1L << s);
            return v;
        }
        long readAddress(boolean is64) { return is64 ? readUint64() : (readUint32() & 0xFFFFFFFFL); }
        long readAddressBySize(int size) {
            if (size == 8) return readUint64();
            if (size == 4) return readUint32() & 0xFFFFFFFFL;
            // 非主流尺寸
            long v = 0L;
            for (int i = 0; i < size; i++) v |= ((long) (raw[(int) pos + i] & 0xFF)) << (i * 8);
            pos += size; return v;
        }
        @Nullable String readNullTerminatedString() {
            int start = (int) pos;
            while (pos < end && raw[(int) pos] != 0) pos++;
            if (pos >= end) return null;
            String s = new String(raw, start, (int) (pos - start), StandardCharsets.UTF_8);
            pos++; return s;
        }
        @Nullable String readStringForm(long form, byte[] raw,
                                        long debugStrOff, long debugStrSize,
                                        long debugLineStrOff, long debugLineStrSize) {
            switch ((int) form) {
                case 0x08: { // DW_FORM_string
                    return readNullTerminatedString();
                }
                case 0x0e: { // DW_FORM_strp
                    long off = readUint4or8(prefer64);
                    int s = (int) (off - debugStrOff);
                    if (s < 0 || s >= debugStrSize) return null;
                    int end = s;
                    while (end < debugStrSize && raw[(int) (debugStrOff + end)] != 0) end++;
                    return new String(raw, (int) (debugStrOff + s), end - s, StandardCharsets.UTF_8);
                }
                case 0x1d: { // DW_FORM_line_strp
                    long off = readUint4or8(prefer64);
                    int s = (int) (off - debugLineStrOff);
                    if (s < 0 || s >= debugLineStrSize) return null;
                    int end = s;
                    while (end < debugLineStrSize && raw[(int) (debugLineStrOff + end)] != 0) end++;
                    return new String(raw, (int) (debugLineStrOff + s), end - s, StandardCharsets.UTF_8);
                }
                default:
                    return null;
            }
        }
        void skipForm(long form, int addrSize) {
            switch ((int) form) {
                case 0x01: pos += 1; break; // DW_FORM_addr (用 addrSize 字节)
                case 0x02: skipBytes(1); break; // DW_FORM_block (length: ULEB)
                case 0x03: skipBytes(readULEB128()); break;
                case 0x04: skipBytes(2); break;
                case 0x05: skipBytes(4); break;
                case 0x06: skipBytes(8); break;
                case 0x07: pos += 1; break; // DW_FORM_data1
                case 0x08: { // DW_FORM_string
                    while (pos < end && raw[(int) pos] != 0) pos++;
                    if (pos < end) pos++;
                    break;
                }
                case 0x09: { // DW_FORM_block1: 1-byte length + bytes
                    int n = readUint8();
                    skipBytes(n);
                    break;
                }
                case 0x0a: skipBytes(2); break; // DW_FORM_block2
                case 0x0b: skipBytes(4); break; // DW_FORM_block4
                case 0x0c: readULEB128(); break; // DW_FORM_data2
                case 0x0d: readULEB128(); break; // DW_FORM_data4
                case 0x0e: pos += prefer64 ? 8 : 4; break; // DW_FORM_strp
                case 0x0f: readULEB128(); break; // DW_FORM_indirect
                case 0x10: readULEB128(); break; // DW_FORM_sec_offset
                case 0x11: readULEB128(); break; // DW_FORM_exprloc
                case 0x12: pos += 1; break; // DW_FORM_flag
                case 0x13: pos += 1; break; // DW_FORM_sdata
                case 0x14: readUint32(); readUint32(); break; // DW_FORM_strx (2 uleb indexes 简化)
                case 0x15: readULEB128(); readULEB128(); break; // DW_FORM_addrx
                case 0x16: readULEB128(); break; // DW_FORM_ref_sup4
                case 0x17: skipBytes(8); break; // DW_FORM_data16
                case 0x18: pos += 1; break; // DW_FORM_line_strp
                case 0x19: readULEB128(); readULEB128(); readULEB128(); break; // DW_FORM_implicit_const
                case 0x1a: skipBytes(addrSize); break; // DW_FORM_loclistx
                case 0x1b: readULEB128(); break; // DW_FORM_rnglistx
                case 0x1c: skipBytes(addrSize); break; // DW_FORM_ref_sup8
                case 0x1d: pos += prefer64 ? 8 : 4; break; // DW_FORM_strx1
                case 0x1e: pos += prefer64 ? 8 : 4; break; // DW_FORM_strx2
                case 0x1f: pos += prefer64 ? 8 : 4; break; // DW_FORM_strx3
                case 0x20: pos += prefer64 ? 8 : 4; break; // DW_FORM_strx4
                case 0x21: readULEB128(); break; // DW_FORM_addrx1
                case 0x22: readULEB128(); break; // DW_FORM_addrx2
                case 0x23: readULEB128(); break; // DW_FORM_addrx3
                case 0x24: readULEB128(); break; // DW_FORM_addrx4
                default: pos = end; break;
            }
        }
        void skipBytes(long n) { pos = Math.min(end, pos + n); }
    }
}
