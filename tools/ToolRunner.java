package tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ToolRunner {
    private static final Map<String, String> PYTHON_SCRIPTS = new HashMap<>();
    private static final Map<String, String> JAVA_TOOLS = new HashMap<>();

    private static final Map<String, Boolean> SCRIPT_IN_TOOLS = new HashMap<>();

    static {
        PYTHON_SCRIPTS.put("generate-html", "generate_html.py");
        PYTHON_SCRIPTS.put("validate-maps", "validate_maps.py");
        PYTHON_SCRIPTS.put("generate-tmx", "opencode/skills/tmx-map-generator/scripts/generate_tmx.py");

        JAVA_TOOLS.put("update-maps", "tools.MapUpdateTool");

        SCRIPT_IN_TOOLS.put("generate-html", true);
        SCRIPT_IN_TOOLS.put("validate-maps", true);
        SCRIPT_IN_TOOLS.put("generate-tmx", false);
    }

    public static void main(String[] args) {
        if (args.length > 0 && PYTHON_SCRIPTS.containsKey(args[0])) {
            runPythonScript(args[0], null);
            return;
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("--- Tool Runner ---");
        System.out.println("Available scripts/tools:");
        int i = 1;
        Map<Integer, String> menuMap = new HashMap<>();
        
        for (String key : PYTHON_SCRIPTS.keySet()) {
            System.out.println(i + ". [Python] " + key);
            menuMap.put(i, key);
            i++;
        }
        for (String key : JAVA_TOOLS.keySet()) {
            System.out.println(i + ". [Java] " + key);
            menuMap.put(i, key);
            i++;
        }
        
        System.out.println("0. Exit");
        System.out.print("Select an option: ");

        if (scanner.hasNextInt()) {
            int choice = scanner.nextInt();
            scanner.nextLine(); // consume newline
            if (choice == 0) return;
            if (menuMap.containsKey(choice)) {
                String key = menuMap.get(choice);
                if (PYTHON_SCRIPTS.containsKey(key)) {
                    String defaultFile = null;
                    if (key.equals("validate-maps")) {
                        defaultFile = "map_validation.txt";
                    } else if (key.equals("generate-html")) {
                        defaultFile = "map-design-for-tiled.html";
                    }
                    System.out.print("Enter output filename" + (defaultFile != null ? " (or press Enter for " + defaultFile + ")" : "") + ": ");
                    String outputFile = scanner.nextLine().trim();
                    if (outputFile.isEmpty()) {
                        outputFile = defaultFile;
                    }
                    runPythonScript(key, outputFile);
                } else {
                    // Java Tool Logic
                    System.out.print("Enter directory (or press Enter for assets/maps): ");
                    String dir = scanner.nextLine().trim();
                    if (dir.isEmpty()) dir = "assets/maps";
                    System.out.print("Enter divisor (or press Enter for 16): ");
                    String divisor = scanner.nextLine().trim();
                    if (divisor.isEmpty()) divisor = "16";
                    runJavaTool(key, dir, divisor);
                }
            } else {
                System.out.println("Invalid selection.");
            }
        }
    }

    private static void runPythonScript(String scriptKey, String outputFile) {
        String scriptPath = PYTHON_SCRIPTS.get(scriptKey);
        // If we are currently in the tools directory, we don't need the prefix
        String workingDir = System.getProperty("user.dir");
        boolean inTools = workingDir.endsWith("/tools") || workingDir.endsWith("\\tools");
        String prefix = (SCRIPT_IN_TOOLS.get(scriptKey)) ? "tools/" : "";
        String command = "python3 " + prefix + scriptPath;
        System.out.println("Running: " + command);
        try {
            // Set the execution context to the project root so it can find 'assets/'
            File projectRoot = inTools ? new File(workingDir).getParentFile() : new File(workingDir);
            ProcessBuilder pb = new ProcessBuilder(command.split(" "));
            pb.directory(projectRoot);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedWriter writer = (outputFile != null) ? new BufferedWriter(new FileWriter(outputFile)) : null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    if (writer != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
            if (writer != null) {
                writer.close();
                System.out.println("Result written to " + outputFile);
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runJavaTool(String toolKey, String dir, String divisor) {
        String className = JAVA_TOOLS.get(toolKey);
        String command = "java -cp .:" + System.getProperty("java.class.path") + " " + className + " " + dir + " " + divisor;
        System.out.println("Running: " + command);
        try {
            ProcessBuilder pb = new ProcessBuilder(command.split(" "));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
