package com.zerostudio.language.source;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能 SourceLocator：在多个候选 SourceLocator 之间按规则选择最优。
 *
 * 场景：
 *  - 同包同名类出现在多个 jar（如不同版本、不同 shaded 副本）
 *  - 调试器要选择最匹配运行时版本的源码
 *  - 测试/构建期要选择最新源码
 *
 * 选择规则（按优先级降序）：
 *  1. 用户显式指定的 preferVersion（"1.2" / "2.0"）
 *  2. shaded jar 优先（名字带 -shaded 后缀）
 *  3. 文件大小（class 大者优先，更可能包含完整内容）
 *  4. 较新文件（最后修改时间）优先
 *  5. 字母序
 */
public final class SmartSourceLocator {

    public static final class Score {
        public final SourceLocator.LocatedSource source;
        public final double total;
        public final Map<String, Double> components;

        public Score(SourceLocator.LocatedSource source, double total, Map<String, Double> components) {
            this.source = source;
            this.total = total;
            this.components = components;
        }
    }

    public enum SelectionStrategy { PREFER_SHADED, PREFER_LATEST, PREFER_LARGEST }

    private SelectionStrategy strategy = SelectionStrategy.PREFER_SHADED;
    private String preferVersion = null;

    public void setStrategy(SelectionStrategy strategy) { this.strategy = strategy; }
    public void setPreferVersion(String v) { this.preferVersion = v; }

    /**
     * 给定多个候选源（来自不同的 jar/workspace/反编译），
     * 按当前策略打分并返回最优。
     */
    public SourceLocator.LocatedSource select(List<SourceLocator.LocatedSource> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        Score best = null;
        for (SourceLocator.LocatedSource c : candidates) {
            Score s = score(c);
            if (best == null || s.total > best.total) best = s;
        }
        return best == null ? null : best.source;
    }

    /** 给每个候选打分（用于排序/调试） */
    public List<Score> scoreAll(List<SourceLocator.LocatedSource> candidates) {
        List<Score> out = new ArrayList<>();
        if (candidates == null) return out;
        for (SourceLocator.LocatedSource c : candidates) out.add(score(c));
        return out;
    }

    private Score score(SourceLocator.LocatedSource s) {
        Map<String, Double> comp = new HashMap<>();
        double total = 0;
        if (s == null) return new Score(null, -1, comp);

        // 1. 用户指定版本匹配
        if (preferVersion != null && s.originPath != null && s.originPath.contains(preferVersion)) {
            comp.put("version", 1000.0);
            total += 1000;
        } else {
            comp.put("version", 0.0);
        }

        // 2. shaded jar 优先
        if (strategy == SelectionStrategy.PREFER_SHADED
                && s.originPath != null
                && (s.originPath.contains("-shaded") || s.originPath.contains("shaded"))) {
            comp.put("shaded", 500.0);
            total += 500;
        }

        // 3. 文件大小（class 大者优先）
        if (strategy == SelectionStrategy.PREFER_LARGEST
                && s.originPath != null
                && s.kind == SourceLocator.Kind.DECOMPILED) {
            double size = classFileSize(s.originPath);
            if (size > 0) {
                comp.put("size", Math.log10(size) * 50);
                total += Math.log10(size) * 50;
            }
        }

        // 4. 较新文件优先
        if (strategy == SelectionStrategy.PREFER_LATEST) {
            double mtime = classFileMtime(s.originPath);
            if (mtime > 0) {
                // 距 epoch 的天数 / 100，作为分数
                comp.put("mtime", mtime / 1e12);
                total += mtime / 1e12;
            }
        }

        // 5. 字母序（基础分，避免完全平局）
        if (s.originPath != null) {
            comp.put("path", 1.0 - (s.originPath.hashCode() & 0x7F) / 1000.0);
            total += comp.get("path");
        }
        return new Score(s, total, comp);
    }

    private double classFileSize(String originPath) {
        if (originPath == null) return 0;
        File f = new File(originPath);
        if (f.isFile()) return f.length();
        return 0;
    }

    private double classFileMtime(String originPath) {
        if (originPath == null) return 0;
        File f = new File(originPath);
        if (f.isFile()) return f.lastModified();
        return 0;
    }
}
