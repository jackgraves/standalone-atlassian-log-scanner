package co.uk.jackgraves.logscanner;

import co.uk.jackgraves.logscanner.xml.ObjectStream;
import co.uk.jackgraves.logscanner.xml.RegExItem;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Log Scanner for Atlassian Applications
 *
 * Arguments:   Product (jira-core, jira-soft, jira-desk, confluence, crowd, bitbucket)
 * Log File:    Location of Log File (e.g. atlassian-jira.log)
 *
 * e.g. java -jar logscanner.jar "jira-core" "atlassian-jira.log"
 *
 */
@SuppressWarnings("ALL")
public class Main {
    private static final String[] PRODUCT_DEFINITIONS = {
            "https://confluence.atlassian.com/support/files/179443532/792496554/2342/1525743696518/jira_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792496607/2364/1525741337514/greenhopper_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792630916/2322/1525746325041/servicedesk_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792496589/2365/1525737479913/confluence_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792630164/2408/1525735731825/bamboo_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792303609/2314/1525744113860/stash_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792630874/2361/1525737651612/crowd_regex_v2.xml"
    };
    
    private static final String COMPLETE = "      Complete\r\n";

    public static void main(String[] args) {
        // Introduction
        print("[ Standalone Atlassian Log Scanner - Started ]\r\n\r\n");
        // Download Definition
        print("[1/5] Downloading Definitions... \r\n");
        if(!new File(args[0] + ".xml").isFile()) {
            downloadDefinition(args[0]);
            print(COMPLETE);
        } else {
            print("      Skipping (Already Downloaded)\r\n");
        }
        // Process XML
        print("[2/5] Parsing XML... \r\n");
        List<RegExItem> regexItems = Objects.requireNonNull(unmarshallXml(args[0])).regexItems;
        print(COMPLETE);
        // Build RegEx List
        print("[3/5] Generating Regular Expressions... \r\n");
        HashMap<String, Pattern> regularExpressions = new HashMap<>();
        for (RegExItem regexItem : regexItems) {
            regularExpressions.put(regexItem.URL, Pattern.compile(regexItem.regex));
        }
        print(COMPLETE);
        // Read Log File
        print("[4/5] Reading Log File... \r\n");
        ArrayList<String> logFile = readLogFile(args[1]);
        print(COMPLETE);
        // Parse Logs
        print("[5/5] Parsing Log Lines... \r\n");
        ArrayList<String> errors = parseLog(logFile, regularExpressions);
        print("\r      Complete\r\n");
        // Print Errors
        print("\r\nDetected Problems:\r\n");
        for(String url : errors) {
            print("      " + url + "\r\n");
        }
        print("\r\n[ Standalone Atlassian Log Scanner - Finished ]\r\n");
    }

    private static String getDefinitionUrl(String product) {
        switch(product) {
            case "jira-core": return PRODUCT_DEFINITIONS[0];
            case "jira-soft": return PRODUCT_DEFINITIONS[1];
            case "jira-desk": return PRODUCT_DEFINITIONS[2];
            case "confluence": return PRODUCT_DEFINITIONS[3];
            case "bamboo": return PRODUCT_DEFINITIONS[4];
            case "bitbucket": return PRODUCT_DEFINITIONS[5];
            case "crowd": return PRODUCT_DEFINITIONS[6];
            default: return PRODUCT_DEFINITIONS[0];
        }
    }

    private static void downloadDefinition(String product) {
        try {
            URL website = new URL(getDefinitionUrl(product));
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(product + ".xml");
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ObjectStream unmarshallXml(String product) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ObjectStream.class);
            Unmarshaller jaxbUnmarshaller;
            jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            return (ObjectStream) jaxbUnmarshaller.unmarshal( new File(product + ".xml") );
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static ArrayList<String> readLogFile(String location) {
        String line;
        ArrayList<String> loglines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(location))) {
            while ((line = br.readLine()) != null) {
                loglines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return loglines;
    }

    private static ArrayList<String> parseLog(List<String> logFile, Map<String, Pattern> regularExpressions) {
        ArrayList<String> errors = new ArrayList<>();
        int count = 0;
        int last = 0;
        int size = logFile.size();
        Matcher matcher;
        // TODO: Increase speed. Look at using parrallel worker threads.
        for (String s : logFile) {
            for(String p : regularExpressions.keySet()) {
                matcher = regularExpressions.get(p).matcher(s);
                if(matcher.find()) {
                    if(!errors.contains(p))
                    errors.add(p);
                }
            }
            count++;
            last = printPercentage(count, last, size);
        }
        return errors;
    }

    private static int printPercentage(int count, int last, int size) {
        int percentage = (int) (((double) count / (double) size) * 100);
        if(last != percentage) {
            print("\r      " + percentage + "%");
        }
        return percentage;
    }

    private static void print(CharSequence text) {
        System.out.print(text);
    }
}