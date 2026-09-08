package tools;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import java.io.File;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

/**
 * Bulk updates Tiled .tmx properties from pixel values to tile counts.
 * Usage: java MapUpdateTool.java <directory> <divisor>
 */
public class MapUpdateTool {
    private static final List<String> TARGET_KEYS = Arrays.asList(
        "amplitudeX", "amplitudeY", "patrolRange", "attackRange", "meleeRange"
    );

    private static float divisor;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java MapUpdateTool <directory> <divisor>");
            return;
        }

        String dirPath = args[0];
        divisor = Float.parseFloat(args[1]);
        // For reversal/fix: read current, round to nearest int, write as int.
        Files.walk(Paths.get(dirPath))
             .filter(p -> p.toString().endsWith(".tmx"))
             .forEach(p -> processFileRevert(p.toFile()));
    }

    private static void processFileRevert(File file) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);
            doc.getDocumentElement().normalize();

            NodeList properties = doc.getElementsByTagName("property");
            boolean modified = false;

            for (int i = 0; i < properties.getLength(); i++) {
                Element prop = (Element) properties.item(i);
                String name = prop.getAttribute("name");
                if (TARGET_KEYS.contains(name)) {
                    String valueStr = prop.getAttribute("value");
                    try {
                        float val = Float.parseFloat(valueStr);
                        // Multiply back by 16 if we want to restore original state,
                        // OR just round to nearest int to make it compatible with Integer.parseInt()
                        int newVal = (int) (val * divisor);
                        String finalValue = String.valueOf(newVal);
                        
                        if (!valueStr.equals(finalValue)) {
                            prop.setAttribute("value", finalValue);
                            System.out.println(file.getName() + ": Reverted " + name + " " + valueStr + " -> " + finalValue);
                            modified = true;
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Could not parse " + name + " in " + file.getName());
                    }
                }
            }

            if (modified) {
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer t = tf.newTransformer();
                t.transform(new DOMSource(doc), new StreamResult(file));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
