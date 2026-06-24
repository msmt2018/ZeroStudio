package com.zerostudio.language.service;

import com.zerostudio.language.index.DefaultProjectIndex;
import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import org.junit.Before;
import org.junit.Test;
import java.util.Arrays;
import java.util.Set;
import static org.junit.Assert.*;

public class TypeHierarchyServiceTest {

    private ProjectIndex idx;
    private TypeHierarchyService svc;

    @Before
    public void setUp() {
        idx = new DefaultProjectIndex();
        // A.java: class Animal {}
        ParsedFile a = new ParsedFile("Animal.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("Animal", new SourceRange(new SourcePosition("Animal.java", 1, 1), new SourcePosition("Animal.java", 1, 7)),
                                Reference.ReferenceKind.CLASS, "com.x", "Animal.java", LanguageId.JAVA)
                ), "package com.x;\nclass Animal {}\n");
        idx.index(a);
        // Dog.java: class Dog extends Animal {}
        ParsedFile dog = new ParsedFile("Dog.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("Dog", new SourceRange(new SourcePosition("Dog.java", 1, 1), new SourcePosition("Dog.java", 1, 4)),
                                Reference.ReferenceKind.CLASS, "com.x", "Dog.java", LanguageId.JAVA)
                ), "package com.x;\nclass Dog extends Animal {}\n");
        idx.index(dog);
        // Puppy.java: class Puppy extends Dog {}
        ParsedFile puppy = new ParsedFile("Puppy.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("Puppy", new SourceRange(new SourcePosition("Puppy.java", 1, 1), new SourcePosition("Puppy.java", 1, 6)),
                                Reference.ReferenceKind.CLASS, "com.x", "Puppy.java", LanguageId.JAVA)
                ), "package com.x;\nclass Puppy extends Dog {}\n");
        idx.index(puppy);
        svc = new TypeHierarchyService(idx);
    }

    @Test
    public void directSupertype() {
        Set<String> supers = svc.supertypesOf("com.x.Dog");
        assertTrue(supers.contains("com.x.Animal"));
    }

    @Test
    public void indirectSupertype() {
        Set<String> supers = svc.supertypesOf("com.x.Puppy");
        assertTrue("Puppy should have com.x.Animal in supers, got: " + supers,
                supers.contains("com.x.Animal"));
    }

    @Test
    public void directSubtype() {
        Set<String> subs = svc.subtypesOf("com.x.Dog");
        assertTrue(subs.contains("com.x.Puppy"));
    }

    @Test
    public void indirectSubtype() {
        Set<String> subs = svc.subtypesOf("com.x.Animal");
        assertTrue("Animal should have com.x.Puppy as indirect subtype, got: " + subs,
                subs.contains("com.x.Puppy"));
    }

    @Test
    public void noSupertypesForRoot() {
        Set<String> supers = svc.supertypesOf("com.x.Animal");
        assertTrue(supers.isEmpty());
    }

    @Test
    public void noSubtypesForLeaf() {
        Set<String> subs = svc.subtypesOf("com.x.Puppy");
        assertTrue(subs.isEmpty());
    }

    @Test
    public void nullFqnHandled() {
        assertTrue(svc.supertypesOf(null).isEmpty());
        assertTrue(svc.subtypesOf(null).isEmpty());
    }

    @Test
    public void implementsAlsoCounted() {
        // Runnable 模拟为已索引类
        ParsedFile runnable = new ParsedFile("Runnable.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("Runnable", new SourceRange(new SourcePosition("Runnable.java", 1, 1), new SourcePosition("Runnable.java", 1, 9)),
                                Reference.ReferenceKind.CLASS, "com.x", "Runnable.java", LanguageId.JAVA)
                ), "package com.x;\ninterface Runnable {}\n");
        idx.index(runnable);
        ParsedFile task = new ParsedFile("Task.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("Task", new SourceRange(new SourcePosition("Task.java", 1, 1), new SourcePosition("Task.java", 1, 5)),
                                Reference.ReferenceKind.CLASS, "com.x", "Task.java", LanguageId.JAVA)
                ), "package com.x;\nclass Task implements Runnable {}\n");
        idx.index(task);
        Set<String> supers = svc.supertypesOf("com.x.Task");
        assertTrue("expected Runnable in supers, got: " + supers, supers.contains("com.x.Runnable"));
    }
}
