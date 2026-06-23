#!/bin/bash
# 编译并运行所有 ide-decompiler + ide-language 单元测试
set -e

JUNIT=/root/.local/share/mise/installs/gradle/8.14.4/gradle-8.14.4/lib/junit-4.13.2.jar
HAMCREST=/root/.local/share/mise/installs/gradle/8.14.4/gradle-8.14.4/lib/hamcrest-core-1.3.jar
JAVAPARSER=/root/.local/share/mise/installs/gradle/8.14.4/gradle-8.14.4/lib/javaparser-core-3.17.0.jar
CFR=/workspace/decompile/cfr-0.152.jar
WORK=/tmp/ide-lang-test
SRC=/workspace

mkdir -p $WORK/classes $WORK/test-classes

echo "==> 清理 classes 缓存"
rm -rf $WORK/classes/* $WORK/test-classes/*

echo "==> 编译 ide-decompiler + ide-language main"
cd $SRC
find ide-decompiler/src/main/java ide-language/src/main/java -name "*.java" > /tmp/sources.txt
javac -d $WORK/classes -cp "$JAVAPARSER:$CFR" -encoding UTF-8 @/tmp/sources.txt 2>&1 | tail -20

echo "==> 编译 tests"
find ide-decompiler/src/test/java ide-language/src/test/java -name "*.java" > /tmp/test-sources.txt
javac -d $WORK/test-classes -cp "$WORK/classes:$JUNIT:$HAMCREST:$JAVAPARSER:$CFR" -encoding UTF-8 @/tmp/test-sources.txt 2>&1 | tail -20

echo "==> 运行 JUnit 4 tests"
CP="$WORK/classes:$WORK/test-classes:$JUNIT:$HAMCREST:$JAVAPARSER:$CFR"
TESTS=$(find $WORK/test-classes -name "*Test.class" | sed 's|.*/||;s|\.class||' | sort -u)
PASS=0
FAIL=0
FAILED_TESTS=""
for t in $TESTS; do
  CLS=$(find $WORK/test-classes -name "${t}.class" | sed "s|$WORK/test-classes/||;s|/|.|g;s|\.class$||" | head -1)
  OUT=$(java -cp "$CP" org.junit.runner.JUnitCore "$CLS" 2>&1 | tail -25)
  if echo "$OUT" | grep -q "OK ("; then
    PASS=$((PASS+1))
    echo "  PASS $t"
  else
    FAIL=$((FAIL+1))
    FAILED_TESTS="$FAILED_TESTS $t"
    echo "  FAIL $t"
    echo "$OUT" | tail -10 | sed 's/^/    /'
  fi
done
echo
echo "==> Result: $PASS passed, $FAIL failed"
if [ -n "$FAILED_TESTS" ]; then
  echo "==> Failed:$FAILED_TESTS"
fi
