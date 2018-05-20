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
import java.util.stream.Collectors;

/**
 * Log Scanner for Atlassian Applications
 *
 * Arguments:
 *  -def (Definitions) - e.g. -def=jira-core | jira-soft | jira-desk | confluence | crowd | bitbucket | Custom Definition URL
 *  -log (Location of Log File) - e.g. -log=atlassian-jira.log
 *  -stream (Run in Parrallel) - e.g. -stream
 *  -verbose (Show all instances of an error) - e.g. -verbose
 *
 * Example:
 *  java -jar log-scanner.jar -def=jira-core -log=atlassian-jira.log -stream
 */
@SuppressWarnings("ALL")
public class LogScanner {
    private static final HashMap<String,String> PRODUCT_DEFINITIONS = new HashMap<String,String>() {{
            put("jira-core", "https://confluence.atlassian.com/support/files/179443532/792496554/2342/1525743696518/jira_regex_v2.xml");
            put("jira-soft", "https://confluence.atlassian.com/support/files/179443532/792496607/2364/1525741337514/greenhopper_regex_v2.xml");
            put("jira-desk", "https://confluence.atlassian.com/support/files/179443532/792630916/2322/1525746325041/servicedesk_regex_v2.xml");
            put("confluence", "https://confluence.atlassian.com/support/files/179443532/792496589/2365/1525737479913/confluence_regex_v2.xml");
            put("bamboo", "https://confluence.atlassian.com/support/files/179443532/792630164/2408/1525735731825/bamboo_regex_v2.xml");
            put("bitbucket", "https://confluence.atlassian.com/support/files/179443532/792303609/2314/1525744113860/stash_regex_v2.xml");
            put("crowd", "https://confluence.atlassian.com/support/files/179443532/792630874/2361/1525737651612/crowd_regex_v2.xml");
    }};

    private static final String RETURN = "\r\n";
    private static final String SPACING = "      ";
    private static final String COMPLETE = SPACING + "Complete" + RETURN;
    private static final Pattern DATE_REGEX = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2})");

    public static void main(String[] args) {
        // Initialise Variables
        boolean stream = false;
        boolean verbose = false;
        String logFile = null;
        String defInput = null;

        // Options
        Options opt = new Options(args, 2);
        opt.getSet().addOption("stream", Options.Multiplicity.ZERO_OR_ONE);
        opt.getSet().addOption("verbose", Options.Multiplicity.ZERO_OR_ONE);
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
        if (opt.getSet().isSet("verbose")) {
            verbose = true;
        }

        // Run Scanner
        runScanner(defInput, logFile, stream, verbose);
    }

    private static void runScanner(String definition, String logFile, boolean stream, boolean verbose) {
        // Introduction
        String mode = "Sequential";
        if(stream) mode = "Parrallel";
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
        HashMap<String, Pattern> regularExpressions = regexItems.stream().collect(
                Collectors.toMap(
                        regexItem -> regexItem.URL,
                        regexItem -> Pattern.compile(regexItem.regex),
                        (a, b) -> b,
                        HashMap::new
                )
        );
        print(COMPLETE);

        // Read Log File
        print("[4/5] Reading Log File..." + RETURN);
        ArrayList<String> log = readLogFile(logFile);
        print(COMPLETE);

        // Parse Logs
        print("[5/5] Parsing Log Lines..." + RETURN);
        ArrayList<Result> errors;
        errors = stream ? parseLogStream(log, regularExpressions, verbose) : parseLog(log, regularExpressions, verbose);
        print("\r" + COMPLETE);

        // Print Errors
        print(RETURN + "Detected Problems:" + RETURN);
        for(Result err : errors) {
            print(err.getDate() != null ? SPACING + err.getUrl() + " (" + err.getDate() + ")" + RETURN : SPACING + err.getUrl() + RETURN);
        }
        print(RETURN + "[ Standalone Atlassian Log Scanner - Finished ]" + RETURN);
    }

    private static String getDefinitionUrl(String product) {
        return PRODUCT_DEFINITIONS.getOrDefault(product,PRODUCT_DEFINITIONS.get("jira-core"));
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
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            return (ObjectStream) jaxbUnmarshaller.unmarshal(new File(getFileName(defInput)));
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

    private static ArrayList<Result> parseLog(List<String> logFile, Map<String, Pattern> regularExpressions, boolean verbose) {
        ArrayList<Result> errors = new ArrayList<>();
        int count = 0;
        int last = 0;
        int size = logFile.size();
        Matcher matcher;
        for (String line : logFile) {
            for(String url : regularExpressions.keySet()) {
                matcher = regularExpressions.get(url).matcher(line);
                if(matcher.find()) {
                    if(!errors.contains(url) || verbose) {
                        errors.add(new Result(line, url));
                    }
                }
            }
            count++;
            last = printPercentage(count, last, size);
        }
        return errors;
    }

    private static ArrayList<Result> parseLogStream(List<String> logFile, Map<String, Pattern> regularExpressions, boolean verbose) {
        ArrayList<Result> errors = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger last = new AtomicInteger();
        int size = logFile.size();
        final Matcher[] matcher = new Matcher[1];
        logFile.parallelStream()
                .forEach(line ->{
                    Matcher dateMatcher = DATE_REGEX.matcher(line);
                    String date;
                    date = dateMatcher.find() ? dateMatcher.group(1) + " " + dateMatcher.group(2) : null;
                    for(String url : regularExpressions.keySet()) {
                        matcher[0] = regularExpressions.get(url).matcher(line);
                        if(matcher[0].find()) {
                            Result result = new Result(url, line, date);
                            if(!errors.contains(result) || verbose) {
                                errors.add(result);
                            }
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
            return String.valueOf(defInput.hashCode()) + ".xml";
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