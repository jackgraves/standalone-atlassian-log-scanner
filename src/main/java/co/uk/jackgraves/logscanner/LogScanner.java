package co.uk.jackgraves.logscanner;

import co.uk.jackgraves.logscanner.options.Options;
import co.uk.jackgraves.logscanner.xml.ObjectStream;
import co.uk.jackgraves.logscanner.xml.RegExItem;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Log Scanner for Atlassian Applications.  Supports:
 *  - Sequential and Parallel Modes
 *  - Atlassian or Custom Definition File
 *
 * Arguments:
 *  -def (Definitions) - 1 of jira-core | jira-soft | jira-desk | confluence | crowd | bitbucket or a custom definition URL
 *  -log (Location of Log File) - e.g. atlassian-jira.log
 *  -stream (Run in Parrallel) - flag (set/unset)
 *
 * e.g. java -jar log-scanner.jar -def=jira-core -log=atlassian-jira.log -stream
 *
 */
@SuppressWarnings("ALL")
public class LogScanner {
    private static final String[] PRODUCT_DEFINITIONS = {
            "https://confluence.atlassian.com/support/files/179443532/792496554/2342/1525743696518/jira_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792496607/2364/1525741337514/greenhopper_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792630916/2322/1525746325041/servicedesk_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792496589/2365/1525737479913/confluence_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792630164/2408/1525735731825/bamboo_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792303609/2314/1525744113860/stash_regex_v2.xml",
            "https://confluence.atlassian.com/support/files/179443532/792630874/2361/1525737651612/crowd_regex_v2.xml"
    };
    private static final String RETURN = "\r\n";
    private static final String SPACING = "      ";
    private static final String COMPLETE = SPACING + "Complete" + RETURN;

    public static void main(String[] args) {
        // Initialise Variables
        boolean stream = false;
        String logFile = null;
        String defInput = null;

        // Options
        Options opt = new Options(args, 2);
        opt.getSet().addOption("stream", Options.Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("log", Options.Separator.EQUALS, Options.Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("def", Options.Separator.EQUALS, Options.Multiplicity.ZERO_OR_ONE);
        opt.check();

        // Respond to Options
        if (opt.getSet().isSet("log")) {
            logFile = opt.getSet().getOption("log").getResultValue(0);
        } else {
            print("Error: No Log File Specified (-log=xxx.log)" + RETURN);
            print("Please specify a log file to analyse" + RETURN);
            System.exit(1);
        }
        if (opt.getSet().isSet("def")) {
            defInput = opt.getSet().getOption("def").getResultValue(0);
        } else {
            print("Error: No Definition Specified (-def=jira-core)" + RETURN);
            print("Please specify [jira-core, jira-soft, jira-desk, confluence, bitbucket, bamboo, crowd] or a definition URL (http/https)" + RETURN);
            System.exit(1);
        }
        if (opt.getSet().isSet("stream")) {
            stream = true;
        }

        // Run Scanner
        runScanner(defInput, logFile, stream);
    }

    private static void runScanner(String definition, String logFile, boolean stream) {
        // Introduction
        String mode = "Sequential";
        if(stream) {
            mode = "Parrallel";
        }
        print("[ Standalone Atlassian Log Scanner - Started (" + mode + " Mode) ]" + RETURN + RETURN);

        // Download Definition
        print("[1/5] Downloading Definitions..." + RETURN);
        if(!new File(getFileName(definition)).isFile()) {
            downloadDefinition(definition);
            print(COMPLETE);
        } else {
            print(SPACING + "Skipping (Already Downloaded)" + RETURN);
        }

        // Process XML
        print("[2/5] Parsing XML..." + RETURN);
        List<RegExItem> regexItems = Objects.requireNonNull(unmarshallXml(definition).regexItems);
        print(COMPLETE);

        // Build RegEx List
        print("[3/5] Generating Regular Expressions..." + RETURN);
        HashMap<String, Pattern> regularExpressions = new HashMap<>();
        for (RegExItem regexItem : regexItems) {
            regularExpressions.put(regexItem.URL, Pattern.compile(regexItem.regex));
        }
        print(COMPLETE);

        // Read Log File
        print("[4/5] Reading Log File..." + RETURN);
        ArrayList<String> log = readLogFile(logFile);
        print(COMPLETE);

        // Parse Logs
        print("[5/5] Parsing Log Lines..." + RETURN);
        ArrayList<String> errors;
        if(stream) {
            errors = parseLogStream(log, regularExpressions);
        } else {
            errors = parseLog(log, regularExpressions);
        }
        print("\r" + COMPLETE);

        // Print Errors
        print(RETURN + "Detected Problems:" + RETURN);
        for(String url : errors) {
            print("      " + url + RETURN);
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

    private static void downloadDefinition(String defInput) {
        try {
            URL website = getUrl(defInput);
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(getFileName(defInput));
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static ObjectStream unmarshallXml(String defInput) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ObjectStream.class);
            Unmarshaller jaxbUnmarshaller;
            jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            return (ObjectStream) jaxbUnmarshaller.unmarshal( new File(getFileName(defInput)) );
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

    private static ArrayList<String> parseLogStream(List<String> logFile, Map<String, Pattern> regularExpressions) {
        ArrayList<String> errors = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger last = new AtomicInteger();
        int size = logFile.size();
        final Matcher[] matcher = new Matcher[1];
        logFile.parallelStream()
                .forEach(s ->{
                    for(String p : regularExpressions.keySet()) {
                        matcher[0] = regularExpressions.get(p).matcher(s);
                        if(matcher[0].find()) {
                            if(!errors.contains(p))
                                errors.add(p.toString());
                        }
                    }
                    count.getAndIncrement();
                    last.set(printPercentage(count.get(), last.get(), size));
                });
        return errors;
    }

    private static int printPercentage(int count, int last, int size) {
        int percentage = (int) (((double) count / (double) size) * 100);
        if(last != percentage) {
            print("\r      " + percentage + "%");
        }
        return percentage;
    }

    private static URL getUrl(String defInput) throws MalformedURLException {
        if(isUrl(defInput)) {
            return new URL(defInput);

        } else {
            return new URL(getDefinitionUrl(defInput));
        }
    }

    private static String getFileName(String defInput) {
        if(isUrl(defInput)) {
            CRC32 crc = new CRC32();
            crc.update(defInput.getBytes());
            return String.valueOf(crc.getValue()) + ".xml";
        } else {
            return defInput + ".xml";
        }
    }

    private static boolean isUrl(String input) {
        return input.contains("http") || input.contains("https");
    }

    private static void print(CharSequence text) {
        System.out.print(text);
    }
}