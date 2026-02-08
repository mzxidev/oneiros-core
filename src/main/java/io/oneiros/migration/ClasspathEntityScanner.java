package io.oneiros.migration;

import io.oneiros.annotation.OneirosEntity;
import io.oneiros.annotation.OneirosTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Framework-agnostic classpath scanner for finding @OneirosEntity and @OneirosTable annotated classes.
 *
 * <p>This scanner works without Spring by using reflection and the classloader.
 * It supports both file-based and JAR-based class loading.
 *
 * <p>Usage:
 * <pre>{@code
 * ClasspathEntityScanner scanner = new ClasspathEntityScanner();
 * Set<Class<?>> entities = scanner.scan("com.myapp.domain");
 * }</pre>
 */
public class ClasspathEntityScanner {

    private static final Logger log = LoggerFactory.getLogger(ClasspathEntityScanner.class);

    /**
     * Packages and class name patterns to exclude from entity scanning.
     * This prevents demo, test, and example classes from being migrated.
     * Also excludes internal library packages that should not be scanned
     * when the user's base package is set to something containing 'io.oneiros'.
     */
    private static final Set<String> EXCLUDED_PACKAGES = Set.of(
        "io.oneiros.test",
        "io.oneiros.demo",
        "io.oneiros.example",
        "io.oneiros.query",
        "io.oneiros.domain",
        "io.oneiros.client",
        "io.oneiros.config",
        "io.oneiros.core",
        "io.oneiros.graph",
        "io.oneiros.health",
        "io.oneiros.live",
        "io.oneiros.migration",
        "io.oneiros.pool",
        "io.oneiros.search",
        "io.oneiros.security",
        "io.oneiros.statement",
        "io.oneiros.annotation"
    );

    private static final Set<String> EXCLUDED_CLASS_PATTERNS = Set.of(
        "Demo",
        "Test",
        "Example",
        "Sample"
    );

    /**
     * Scans the given package for @OneirosEntity annotated classes.
     *
     * @param basePackage the package to scan (e.g., "com.myapp.domain")
     * @return a set of classes annotated with @OneirosEntity
     */
    public Set<Class<?>> scan(String basePackage) {
        Set<Class<?>> entities = new HashSet<>();

        if (basePackage == null || basePackage.isEmpty()) {
            log.warn("Base package is null or empty, skipping entity scan");
            return entities;
        }

        String packagePath = basePackage.replace('.', '/');
        ClassLoader classLoader = getClassLoader();

        try {
            Enumeration<URL> resources = classLoader.getResources(packagePath);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    // Scan file-based classes (development mode)
                    String filePath = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8);
                    scanDirectory(new File(filePath), basePackage, entities);
                } else if ("jar".equals(protocol)) {
                    // Scan JAR-based classes (production mode)
                    scanJar(resource, packagePath, entities);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan package: {}", basePackage, e);
        }

        return entities;
    }

    /**
     * Scans for classes that implement or extend a specific type (interface/class).
     *
     * @param basePackage the package to scan
     * @param targetType the interface or class to search for
     * @return a set of classes that implement/extend the target type
     */
    public Set<Class<?>> scanForType(String basePackage, Class<?> targetType) {
        Set<Class<?>> result = new HashSet<>();

        if (basePackage == null || basePackage.isEmpty()) {
            log.warn("Base package is null or empty, skipping type scan");
            return result;
        }

        String packagePath = basePackage.replace('.', '/');
        ClassLoader classLoader = getClassLoader();

        try {
            Enumeration<URL> resources = classLoader.getResources(packagePath);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8);
                    scanDirectoryForType(new File(filePath), basePackage, targetType, result);
                } else if ("jar".equals(protocol)) {
                    scanJarForType(resource, packagePath, targetType, result);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan package: {}", basePackage, e);
        }

        return result;
    }

    /**
     * Scans a directory for classes of a specific type.
     */
    private void scanDirectoryForType(File directory, String packageName, Class<?> targetType, Set<Class<?>> result) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryForType(file, packageName + "." + file.getName(), targetType, result);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                processClassForType(className, targetType, result);
            }
        }
    }

    /**
     * Scans a JAR file for classes of a specific type.
     */
    private void scanJarForType(URL jarUrl, String packagePath, Class<?> targetType, Set<Class<?>> result) {
        try {
            JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
            try (JarFile jarFile = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                        String className = entryName
                            .replace('/', '.')
                            .replace(".class", "");
                        processClassForType(className, targetType, result);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan JAR: {}", jarUrl, e);
        }
    }

    /**
     * Processes a class to check if it implements/extends the target type.
     */
    private void processClassForType(String className, Class<?> targetType, Set<Class<?>> result) {
        if (isExcludedPackage(className) || isExcludedClassName(className)) {
            return;
        }

        try {
            Class<?> clazz = Class.forName(className, false, getClassLoader());

            if (!clazz.isInterface() && targetType.isAssignableFrom(clazz) && !clazz.equals(targetType)) {
                result.add(clazz);
                log.debug("🔎 Found {}: {}", targetType.getSimpleName(), clazz.getSimpleName());
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            log.trace("Class not found or missing dependency: {}", className);
        } catch (Exception e) {
            log.trace("Failed to load class: {} - {}", className, e.getMessage());
        }
    }

    /**
     * Scans a directory for class files.
     */
    private void scanDirectory(File directory, String packageName, Set<Class<?>> entities) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Recurse into subdirectory
                scanDirectory(file, packageName + "." + file.getName(), entities);
            } else if (file.getName().endsWith(".class")) {
                // Process class file
                String className = packageName + "." + file.getName().replace(".class", "");
                processClass(className, entities);
            }
        }
    }

    /**
     * Scans a JAR file for class files.
     */
    private void scanJar(URL jarUrl, String packagePath, Set<Class<?>> entities) {
        try {
            JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
            try (JarFile jarFile = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    // Check if entry is in our package and is a class file
                    if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                        // Convert path to class name
                        String className = entryName
                            .replace('/', '.')
                            .replace(".class", "");
                        processClass(className, entities);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan JAR: {}", jarUrl, e);
        }
    }

    /**
     * Processes a class to check if it has the @OneirosEntity annotation.
     */
    private void processClass(String className, Set<Class<?>> entities) {
        // Skip excluded packages
        if (isExcludedPackage(className)) {
            log.trace("Skipping excluded package: {}", className);
            return;
        }

        // Skip excluded class patterns
        if (isExcludedClassName(className)) {
            log.trace("Skipping excluded class pattern: {}", className);
            return;
        }

        // Skip inner classes from demo/test files
        if (className.contains("$") && isFromDemoOrTestClass(className)) {
            log.trace("Skipping inner class from demo/test: {}", className);
            return;
        }

        try {
            Class<?> clazz = Class.forName(className, false, getClassLoader());

            // Check for @OneirosEntity or @OneirosTable annotation
            if (clazz.isAnnotationPresent(OneirosEntity.class)) {
                entities.add(clazz);
                log.debug("🔎 Found entity: {}", clazz.getSimpleName());
            } else if (clazz.isAnnotationPresent(OneirosTable.class)) {
                entities.add(clazz);
                log.debug("🔎 Found table schema: {}", clazz.getSimpleName());
            }
        } catch (ClassNotFoundException e) {
            log.trace("Class not found: {}", className);
        } catch (NoClassDefFoundError e) {
            // Ignore classes with missing dependencies
            log.trace("Missing dependency for class: {}", className);
        } catch (Exception e) {
            log.trace("Failed to load class: {} - {}", className, e.getMessage());
        }
    }

    /**
     * Checks if the class is in an excluded package.
     */
    private boolean isExcludedPackage(String className) {
        for (String excludedPackage : EXCLUDED_PACKAGES) {
            if (className.startsWith(excludedPackage + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the class name matches an excluded pattern.
     */
    private boolean isExcludedClassName(String className) {
        String simpleName = className.contains(".")
            ? className.substring(className.lastIndexOf('.') + 1)
            : className;

        for (String pattern : EXCLUDED_CLASS_PATTERNS) {
            if (simpleName.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the class is an inner class of a demo/test class.
     */
    private boolean isFromDemoOrTestClass(String className) {
        String outerClassName = className.substring(0, className.indexOf('$'));
        return isExcludedClassName(outerClassName);
    }

    /**
     * Gets the context class loader or system class loader.
     */
    private ClassLoader getClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ClasspathEntityScanner.class.getClassLoader();
        }
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        return classLoader;
    }
}

