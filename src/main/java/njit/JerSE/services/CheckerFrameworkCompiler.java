package njit.JerSE.services;

import njit.JerSE.utils.Configuration;

import java.io.*;

/**
 * Provides functionality for compiling Java classes using the Checker Framework.
 * <p>
 * The Checker Framework enhances Java's type system to make it more powerful and expressive,
 * allowing for early error detection in programs. This service utilizes the framework to
 * compile Java classes and detect potential type errors.
 */
public class CheckerFrameworkCompiler {

    /**
     * Compiles a Java class using the Checker Framework.
     *
     * @param classPath the path to the Java class that needs to be compiled
     * @return a string containing any errors produced during the compilation
     * @throws IOException If there's an error in executing the compilation command or reading its output
     */
    public String compileWithCheckerFramework(String classPath) throws IOException {
        // Compilation command with Checker Framework
        String[] command = compileCheckedClassCommand(classPath);
        Process compileProcess = Runtime.getRuntime().exec(command);
        String errorOutput = streamToString(compileProcess.getErrorStream());
        return extractError(errorOutput);
    }

    /**
     * Captures the content of an input stream into a string.
     *
     * @param stream the input stream to capture
     * @return a string representation of the stream's content
     * @throws IOException If there's an error in reading from the stream
     */
    private static String streamToString(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        return stringBuilder.toString();
    }

    /**
     * Extracts an error message from the provided error output string.
     * <p>
     * Specifically, this method searches for the pattern "error:" within the error output string,
     * which is the pattern used by the Checker Framework to indicate an error. If found, it
     * extracts the message that follows that pattern. The error message can span multiple lines.
     * If the "error:" pattern is not found, an empty string is returned.
     *
     * @param errorMessage the error string to extract messages from
     * @return the extracted error message, or an empty string if the "error:" pattern isn't found
     */
    private String extractError(String errorMessage) {
        /**
         The {@code errorIndex} indicates the position in the input string where the "error:" pattern was found.
         * It can be used to determine the starting point of the extracted error message within the input string.
         * If no "error:" pattern is found, {@code errorIndex} will be -1.
         */
        int errorIndex = errorMessage.indexOf("error:");
        if (errorIndex != -1) {
            // Extract the error message starting from the "error:" pattern
            return errorMessage.substring(errorIndex).trim();
        }
        return ""; // No "error:" pattern found in the errorMessage
    }

    /**
     * Constructs the compilation command for a Java class using the Checker Framework.
     *
     * @param checkedClassPath the path to the Java class to be compiled
     * @return an array of strings representing the compilation command
     */
    private String[] compileCheckedClassCommand(String checkedClassPath) {
        Configuration config = new Configuration();
        String checkerJar = config.getPropertyValue("checker.jar.file");
        String checkerClasspath = config.getPropertyValue("checker.classpath");
        String checkerCommands = config.getPropertyValue("checker.commands");

        return new String[]{
                "java",
                "-jar",
                checkerJar,
                "-cp",
                checkerClasspath,
                "-processor",
                checkerCommands,
                checkedClassPath
        };
    }
}