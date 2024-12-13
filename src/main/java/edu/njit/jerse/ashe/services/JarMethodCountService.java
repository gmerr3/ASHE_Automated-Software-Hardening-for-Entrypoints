package edu.njit.jerse.ashe.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class JarMethodCountService {

  public static void compare(Path sourceJar, Path destJar) throws IOException {
    // pass 1: jar -> set of all method names
    // pass 2: jar -> check all
    var srcMethods = extractMethodsFromJar(sourceJar);
    Map<String, Integer> methodUsageCount =
        analyzeMethodUsage(
            destJar, Map.of(Objects.toString(sourceJar.getFileName()), srcMethods), srcMethods);

    methodUsageCount.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .forEach(entry -> System.out.println(entry.getKey() + " : " + entry.getValue()));
  }

  @SuppressWarnings("keyfor")
  public static List<String> getEntrypoints(Path jarSourceDir, Path jarDestDir) throws IOException {
    Map<String, Set<String>> sourceMethods = new HashMap<>();
    try (var stream = Files.walk(jarSourceDir)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(
              x -> sourceMethods.put(Objects.toString(x.getFileName()), extractMethodsFromJar(x)));
    }
    Set<String> allSourceMethods =
        Set.copyOf(
            sourceMethods.values().stream().flatMap(Set::stream).collect(Collectors.toSet()));
    try (var stream = Files.walk(jarDestDir)) {
      return stream
          .parallel()
          .filter(Files::isRegularFile)
          .map(x -> analyzeMethodUsage(x, sourceMethods, allSourceMethods))
          .flatMap(x -> x.keySet().stream())
          .toList();
    }
  }

  private static Set<String> extractMethodsFromJar(Path jarFile) {
    Set<String> methods = new HashSet<>();
    try (JarFile jar = new JarFile(jarFile.toFile())) {
      Enumeration<JarEntry> entries = jar.entries();

      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class")) {
          ClassReader reader = newClassReader(jar, entry);
          ClassNode classNode = new ClassNode();
          reader.accept(classNode, 0);
          String className = classNode.name.replace('/', '.');
          for (var meth : classNode.methods) {
            MethodNode method = (MethodNode) meth;
            methods.add(convert(className + "." + method.name + method.desc));
          }
        }
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    return methods;
  }

  /** Returns a fully qualified method name for a given bytecode method name. */
  private static String convert(String descriptor) {
    Type[] argumentTypes = Type.getArgumentTypes(descriptor.substring(descriptor.indexOf('(')));
    StringBuilder result = new StringBuilder();
    result
        .append(qualifiedClassName(descriptor))
        .append('(')
        .append(
            Arrays.stream(argumentTypes).map(Type::getClassName).collect(Collectors.joining(", ")))
        .append(')');
    return result.toString();
  }

  /**
   * Extracts the qualified class name from a bytecode method name (i.e. everything before the #)
   */
  private static String qualifiedClassName(String byteName) {
    byteName = byteName.replace('$', '.');
    int lastDot = byteName.split("\\(", 2)[0].lastIndexOf(".");
    var builder = new StringBuilder(byteName);
    builder.setCharAt(lastDot, '#');
    byteName = builder.toString();
    if (byteName.contains("<init>")) {
      int secondLastDot = byteName.substring(0, lastDot).lastIndexOf(".");
      byteName = byteName.replace("<init>", byteName.substring(secondLastDot + 1, lastDot));
    }
    return byteName.replace("/", ".").substring(0, byteName.lastIndexOf(')') + 1).split("\\(")[0];
  }

  /** Utility to create a new ClassReader that reads from an entry in a jar file */
  private static ClassReader newClassReader(JarFile file, ZipEntry entry) throws IOException {
    try (var jis = file.getInputStream(entry)) {
      return new ClassReader(jis.readAllBytes());
    }
  }

  /** Resolve method owner using the class hierarchy map */
  private static @Nullable String resolveMethodOwner(
      String className,
      String methodName,
      String methodDesc,
      Map<String, Set<String>> sourceMethods,
      Map<String, String> hierarchy) {
    for (String name = className; name != null; name = hierarchy.get(className)) {
      Set<String> methods = sourceMethods.get(className);
      if (methods != null && methods.contains(methodName + methodDesc)) {
        return className + "." + methodName + methodDesc;
      }
    }
    return null;
  }

  /** Builds class hierarchy map(subclass -> parent) */
  private static Map<String, String> buildClassHierarchy(Path jarFile) throws IOException {
    Map<String, String> hierarchy = new HashMap<>();
    try (JarFile jar = new JarFile(jarFile.toFile())) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class")) {
          ClassReader reader = newClassReader(jar, entry);
          ClassNode classNode = new ClassNode();
          reader.accept(classNode, 0);

          String className = classNode.name.replace('/', '.');
          if (classNode.superName != null) {
            hierarchy.put(className, classNode.superName.replace('/', '.'));
          }
          for (String iface : classNode.interfaces) {
            hierarchy.put(className, iface.replace('/', '.'));
          }
        }
      }
    }
    return hierarchy;
  }

  private static Map<String, Integer> analyzeMethodUsage(
      Path jarFile, Map<String, Set<String>> sourceMethods, Set<String> allSourceMethods) {
    Map<String, Integer> usageCount = new HashMap<>();
    // Map<String, String> hierarchy = buildClassHierarchy(jarFile);
    try (JarFile jar = new JarFile(jarFile.toFile())) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class")) {
          ClassReader reader = newClassReader(jar, entry);
          ClassNode classNode = new ClassNode();
          reader.accept(classNode, 0);

          for (var meth : classNode.methods) {
            MethodNode method = (MethodNode) meth;
            if (method.instructions == null) continue;

            for (AbstractInsnNode insn : method.instructions) {
              if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                /**
                 * String resolvedMethod = resolveMethodOwner( methodInsn.owner.replace('/', '.'),
                 * methodInsn.name, methodInsn.desc, sourceMethods, hierarchy); if (resolvedMethod
                 * != null) { usageCount.put(resolvedMethod, usageCount.getOrDefault(resolvedMethod,
                 * 0) + 1); }
                 */
                String qualifiedMethod =
                    convert(
                        methodInsn.owner.replace('/', '.')
                            + "."
                            + methodInsn.name
                            + methodInsn.desc);
                Set<String> currentJarMethods;
                if (allSourceMethods.contains(qualifiedMethod)
                    && ((currentJarMethods =
                            sourceMethods.get(Objects.toString(jarFile.getFileName())))
                        != null)
                    && !currentJarMethods.contains(qualifiedMethod)) {
                  usageCount.put(qualifiedMethod, usageCount.getOrDefault(qualifiedMethod, 0) + 1);
                }
              }
            }
          }
        }
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    return usageCount;
  }
}
