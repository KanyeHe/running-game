import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** 使用 JDK 语法树统计方法（含构造器）从声明到闭括号的物理行，包含空行和注释。 */
public class CheckMethodLength {
  static int violations;

  public static void main(String[] args) throws Exception {
    List<String> files = new ArrayList<>();
    for (String arg : args) {
      try (var paths = Files.walk(Path.of(arg))) {
        paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> files.add(p.toString()));
      }
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var manager = compiler.getStandardFileManager(null, null, null)) {
      var task =
          (JavacTask)
              compiler.getTask(
                  null,
                  manager,
                  null,
                  List.of("-proc:none"),
                  null,
                  manager.getJavaFileObjectsFromStrings(files));
      var positions = Trees.instance(task).getSourcePositions();
      for (var unit : task.parse()) scan(unit, positions);
    }
    if (violations > 0) throw new IllegalStateException(violations + " methods exceed 35 lines");
    System.out.println("PASS Java method length <= 35 lines");
  }

  static void scan(CompilationUnitTree unit, SourcePositions positions) {
    new TreeScanner<Void, Void>() {
      @Override
      public Void visitMethod(MethodTree method, Void unused) {
        if (method.getBody() != null) {
          long start = unit.getLineMap().getLineNumber(positions.getStartPosition(unit, method));
          long end = unit.getLineMap().getLineNumber(positions.getEndPosition(unit, method) - 1);
          if (end - start + 1 > 35) {
            System.err.println(
                unit.getSourceFile().getName()
                    + ":"
                    + start
                    + " "
                    + method.getName()
                    + " "
                    + (end - start + 1)
                    + " lines");
            violations++;
          }
        }
        return super.visitMethod(method, unused);
      }
    }.scan(unit, null);
  }
}
