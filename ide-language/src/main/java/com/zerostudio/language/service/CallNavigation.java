package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourceLocation;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.Symbol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Cross-method navigation: "show callers" and "show callees".
 *
 * <p>Callers of a method are the {@link Reference} sites of kind
 * {@code CALL} that point at it. The naive index lookup returns every
 * {@code call()} reference in the project; we then filter by container
 * symbol FQN to narrow to a specific overload.
 *
 * <p>Callees are the call references that originate from the given
 * method's body. We use the {@link Reference#containerFqn} field on each
 * reference to identify them.
 *
 * <p>The service is read-only. It does not modify the index.
 */
public final class CallNavigation {

    private final ProjectIndex index;

    public CallNavigation(ProjectIndex index) {
        this.index = Objects.requireNonNull(index);
    }

    /**
     * Find every call site that may invoke {@code target}.
     *
     * <p>The result is an over-approximation: it is the list of every
     * {@code CALL} reference in the project whose name matches the
     * target's name. The caller can use the source range to disambiguate
     * overloads in the UI.
     */
    public List<CallSite> callersOf(Symbol target) {
        if (target == null) return Collections.emptyList();
        List<CallSite> out = new ArrayList<>();
        for (ParsedFile f : index.lookup().files()) {
            for (Reference r : f.references) {
                if (r.kind != Reference.ReferenceKind.CALL) continue;
                if (!target.name.equals(r.name)) continue;
                out.add(new CallSite(f.path, r));
            }
        }
        return out;
    }

    /**
     * Find every method/function called from inside {@code from}.
     *
     * <p>Returns the names of the called methods and their call sites.
     * Resolving each callee to its declaration is a separate
     * {@link GoToDefinitionService} call.
     */
    public List<CallSite> calleesOf(Symbol from) {
        if (from == null) return Collections.emptyList();
        List<CallSite> out = new ArrayList<>();
        ParsedFile file = index.lookup().file(from.sourceFile);
        if (file == null) return out;
        for (Reference r : file.references) {
            if (r.kind != Reference.ReferenceKind.CALL) continue;
            if (from.fqn != null && !from.fqn.equals(r.containerFqn)) continue;
            out.add(new CallSite(file.path, r));
        }
        return out;
    }

    /**
     * @return a one-line summary used by hover / inline UI.
     */
    public String describeCallers(Symbol target) {
        int n = callersOf(target).size();
        return n + " call site" + (n == 1 ? "" : "s");
    }

    /** A call site: a (file, reference) pair. */
    public static final class CallSite {
        public final String file;
        public final Reference reference;

        public CallSite(String file, Reference reference) {
            this.file = file;
            this.reference = reference;
        }

        public SourceLocation location() {
            return new SourceLocation(file, reference.range.start);
        }

        public SourceRange range() {
            return reference.range;
        }

        @Override
        public String toString() {
            return reference.name + " @ " + file + reference.range;
        }
    }
}
